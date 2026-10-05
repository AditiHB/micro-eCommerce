package com.ecommerce.common.messaging;

import com.ecommerce.common.dlq.DeadLetter;
import com.ecommerce.common.dlq.DeadLetterRepository;
import com.ecommerce.common.dlq.DeadLetterService;
import com.ecommerce.common.events.EventPublisher;
import com.ecommerce.common.events.OrderCreatedEvent;
import com.ecommerce.common.events.Topics;
import com.ecommerce.common.outbox.OutboxEvent;
import com.ecommerce.common.outbox.OutboxRepository;
import com.ecommerce.common.testsupport.EventSamples;
import com.ecommerce.common.testsupport.PostgresIntegrationTest;
import com.ecommerce.common.testsupport.SharedKafka;
import com.ecommerce.common.testsupport.TestMessagingApplication;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * The messaging stack against a real Kafka (KRaft) and PostgreSQL: events written through the outbox arrive
 * typed at a consumer; a transient failure is retried; a permanent failure ends up parked as a dead letter and
 * can be replayed; and a poison or untrusted message is dead-lettered without blocking the partition.
 */
@SpringBootTest(classes = {TestMessagingApplication.class, KafkaDeliveryIntegrationTest.Listeners.class}, properties = {
        "spring.application.name=test-service",
        "spring.kafka.consumer.group-id=test-group",
        "spring.kafka.listener.auto-startup=true",
        "spring.kafka.admin.auto-create=true",
        "ecommerce.dlq.consumer-groups=test-group",
        "ecommerce.outbox.relay-enabled=true",
        "ecommerce.outbox.poll-interval=PT0.1S",
        "kafka.consumer.retry.max-attempts=3",
        "kafka.consumer.retry.initial-interval-ms=50",
        "kafka.consumer.retry.max-interval-ms=200",
        "kafka.consumer.concurrency=1"
})
@PostgresIntegrationTest
// This context runs a live outbox relay against the shared database; close it afterwards so it cannot
// pick up rows that other test classes are inspecting.
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@DisplayName("Kafka delivery: outbox relay, retry, dead letters, replay")
class KafkaDeliveryIntegrationTest {

    private static final Duration WAIT = Duration.ofSeconds(60);

    @DynamicPropertySource
    static void kafka(DynamicPropertyRegistry registry) {
        registry.add("spring.kafka.bootstrap-servers", SharedKafka::bootstrapServers);
    }

    @Autowired
    private EventPublisher publisher;
    @Autowired
    private OutboxRepository outbox;
    @Autowired
    private DeadLetterRepository deadLetters;
    @Autowired
    private DeadLetterService deadLetterService;
    @Autowired
    private PlatformTransactionManager transactionManager;
    @Autowired
    private Listener listener;

    private TransactionTemplate tx;

    @BeforeEach
    void setUp() {
        tx = new TransactionTemplate(transactionManager);
        listener.reset();
    }

    private OrderCreatedEvent order(long id) {
        return new OrderCreatedEvent(id, 7L, EventSamples.lines(), new BigDecimal("172.97"), "USD");
    }

    private void publish(OrderCreatedEvent event) {
        tx.executeWithoutResult(s -> publisher.publish(event));
    }

    private boolean received(long orderId) {
        return listener.received.stream().anyMatch(e -> e.getOrderId() == orderId);
    }

    @Test
    @DisplayName("an event written through the outbox reaches a consumer as a typed, fully-populated object")
    void deliversTypedEvents() {
        publish(order(5001L));

        await().atMost(WAIT).until(() -> received(5001L));

        OrderCreatedEvent event = listener.received.stream().filter(e -> e.getOrderId() == 5001L).findFirst().orElseThrow();
        assertThat(event.getCustomerId()).isEqualTo(7L);
        assertThat(event.getCurrency()).isEqualTo("USD");
        assertThat(event.getTotalAmount()).isEqualByComparingTo("172.97");
        assertThat(event.getLines()).extracting(l -> l.getProductId()).containsExactly("SKU-001", "SKU-002");
        assertThat(event.getOccurredAt()).isNotNull();
        await().atMost(WAIT).untilAsserted(() ->
                assertThat(outbox.findByEventId(event.getEventId())).get().extracting(OutboxEvent::getStatus).isEqualTo(OutboxEvent.PUBLISHED));
    }

    @Test
    @DisplayName("a transient failure is retried with backoff and then succeeds - no dead letter")
    void transientFailureIsRetried() {
        listener.failFirst(2);
        long before = deadLetters.count();

        publish(order(5002L));

        await().atMost(WAIT).until(() -> received(5002L));
        assertThat(listener.attempts.get()).isEqualTo(3);
        assertThat(deadLetters.count()).isEqualTo(before);
    }

    @Test
    @DisplayName("a permanent failure is dead-lettered after the retries, parked, and can be replayed once fixed")
    void permanentFailureIsParkedAndReplayable() {
        listener.alwaysFail = true;

        publish(order(5003L));

        await().atMost(WAIT).until(() -> deadLetters.findAll().stream().anyMatch(d -> d.getPayload() != null && d.getPayload().contains("\"orderId\":5003")));
        DeadLetter parked = deadLetters.findAll().stream().filter(d -> d.getPayload().contains("\"orderId\":5003")).findFirst().orElseThrow();
        assertThat(parked.getStatus()).isEqualTo(DeadLetter.PARKED);
        assertThat(parked.getOriginalTopic()).isEqualTo(Topics.ORDER_CREATED);
        assertThat(parked.getConsumerGroup()).isEqualTo("test-group");
        assertThat(parked.getExceptionMessage()).contains("simulated failure");
        assertThat(parked.getMessageKey()).isEqualTo("5003");
        assertThat(listener.attempts.get()).as("tried 3 times, then dead-lettered").isEqualTo(3);

        // The cause is fixed; an operator replays the letter.
        listener.alwaysFail = false;
        deadLetterService.replay(parked.getId());

        await().atMost(WAIT).until(() -> received(5003L));
        assertThat(deadLetters.findById(parked.getId())).get().satisfies(d -> {
            assertThat(d.getStatus()).isEqualTo(DeadLetter.REPLAYED);
            assertThat(d.getReplayedAt()).isNotNull();
        });
    }

    @Test
    @DisplayName("a poison message is dead-lettered at once and does not block the messages behind it")
    void poisonMessageDoesNotBlockThePartition() {
        try (KafkaProducer<String, String> raw = rawProducer()) {
            ProducerRecord<String, String> poison = new ProducerRecord<>(Topics.ORDER_CREATED, "6000", "this is {not json");
            poison.headers().add("__TypeId__", "order.created".getBytes(StandardCharsets.UTF_8));
            raw.send(poison);
            raw.flush();
        }
        publish(order(6001L)); // a perfectly good event queued right behind it

        await().atMost(WAIT).until(() -> received(6001L));
        await().atMost(WAIT).until(() -> deadLetters.findAll().stream().anyMatch(d -> "this is {not json".equals(d.getPayload())));
        DeadLetter parked = deadLetters.findAll().stream().filter(d -> "this is {not json".equals(d.getPayload())).findFirst().orElseThrow();
        assertThat(parked.getExceptionClass()).contains("DeserializationException");
        assertThat(listener.attempts.get()).as("the unreadable message never reached the listener").isEqualTo(1);
    }

    @Test
    @DisplayName("an unknown type name is refused: only classes in the event catalog are ever instantiated")
    void untrustedTypeIsRefused() {
        try (KafkaProducer<String, String> raw = rawProducer()) {
            ProducerRecord<String, String> hostile = new ProducerRecord<>(Topics.ORDER_CREATED, "6100", "{\"orderId\":6100}");
            hostile.headers().add("__TypeId__", "java.lang.ProcessBuilder".getBytes(StandardCharsets.UTF_8));
            raw.send(hostile);
            raw.flush();
        }

        await().atMost(WAIT).until(() -> deadLetters.findAll().stream().anyMatch(d -> "{\"orderId\":6100}".equals(d.getPayload())));
        assertThat(listener.received).noneMatch(e -> e.getOrderId() == 6100L);
        assertThat(listener.attempts.get()).isZero();
    }

    private static KafkaProducer<String, String> rawProducer() {
        return new KafkaProducer<>(Map.of(
                ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, SharedKafka.bootstrapServers(),
                ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class,
                ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class));
    }

    /** A stand-in consumer whose behaviour the tests control. */
    static class Listener {
        final List<OrderCreatedEvent> received = new CopyOnWriteArrayList<>();
        final AtomicInteger attempts = new AtomicInteger();
        volatile int failTimes;
        volatile boolean alwaysFail;

        void reset() {
            received.clear();
            attempts.set(0);
            failTimes = 0;
            alwaysFail = false;
        }

        void failFirst(int times) {
            failTimes = times;
        }

        @KafkaListener(topics = Topics.ORDER_CREATED, groupId = "test-group")
        public void on(OrderCreatedEvent event) {
            int attempt = attempts.incrementAndGet();
            if (alwaysFail || attempt <= failTimes) {
                throw new IllegalStateException("simulated failure #" + attempt);
            }
            received.add(event);
        }
    }

    @TestConfiguration
    static class Listeners {
        @Bean
        Listener listener() {
            return new Listener();
        }
    }
}
