package com.ecommerce.common.outbox;

import com.ecommerce.common.eventsourcing.EventStoreRepository;
import com.ecommerce.common.events.EventPublisher;
import com.ecommerce.common.events.OrderCancelledEvent;
import com.ecommerce.common.events.Topics;
import com.ecommerce.common.testsupport.EventSamples;
import com.ecommerce.common.testsupport.PostgresIntegrationTest;
import com.ecommerce.common.testsupport.TestMessagingApplication;
import io.micrometer.core.instrument.MeterRegistry;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The transactional outbox against a real PostgreSQL: the event and the business change are one atomic write,
 * and the relay delivers every event at least once, in order per aggregate, without two replicas ever sending
 * the same row.
 */
@SpringBootTest(classes = TestMessagingApplication.class)
@PostgresIntegrationTest
@DisplayName("Transactional outbox")
class OutboxIntegrationTest {

    @Autowired
    private EventPublisher publisher;
    @Autowired
    private OutboxRepository outbox;
    @Autowired
    private EventStoreRepository eventStore;
    @Autowired
    private OutboxProperties properties;
    @Autowired
    private PlatformTransactionManager transactionManager;
    @Autowired
    private ObjectProvider<MeterRegistry> meters;
    @Autowired
    private OutboxMaintenance maintenance;

    @MockBean(name = "outboxKafkaTemplate")
    private KafkaTemplate<String, String> kafka;

    private TransactionTemplate tx;
    private OutboxRelay relay;

    @BeforeEach
    void setUp() {
        tx = new TransactionTemplate(transactionManager);
        outbox.deleteAll();
        eventStore.deleteAll();
        reset(kafka);
        properties.setMaxAttempts(50);
        properties.setBatchSize(50);
        relay = new OutboxRelay(outbox, kafka, properties, transactionManager, meters);
    }

    @SuppressWarnings("unchecked")
    private void kafkaAcknowledges() {
        when(kafka.send(any(ProducerRecord.class))).thenReturn(CompletableFuture.completedFuture((SendResult<String, String>) null));
    }

    @SuppressWarnings("unchecked")
    private void kafkaFails() {
        when(kafka.send(any(ProducerRecord.class))).thenReturn(CompletableFuture.failedFuture(new RuntimeException("broker down")));
    }

    // ------------------------------------------------------------------ the write side

    @Test
    @DisplayName("the event is stored with the business transaction: one commit writes both")
    void eventIsWrittenInTheCallersTransaction() {
        tx.executeWithoutResult(status -> publisher.publish(EventSamples.orderCreated()));

        assertThat(outbox.findAll()).singleElement().satisfies(row -> {
            assertThat(row.getStatus()).isEqualTo(OutboxEvent.PENDING);
            assertThat(row.getTopic()).isEqualTo(Topics.ORDER_CREATED);
            assertThat(row.getMessageKey()).isEqualTo("42");
            assertThat(row.getPayload()).contains("\"totalAmount\":172.97");
        });
        assertThat(eventStore.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("a rolled-back business transaction leaves NO event behind (the dual-write bug)")
    void rollbackDiscardsTheEvent() {
        tx.executeWithoutResult(status -> {
            publisher.publish(EventSamples.orderCreated());
            status.setRollbackOnly();
        });

        assertThat(outbox.count()).isZero();
        assertThat(eventStore.count()).isZero();
    }

    @Test
    @DisplayName("a failure after publishing rolls the event back too")
    void exceptionDiscardsTheEvent() {
        assertThatThrownBy(() -> tx.executeWithoutResult(status -> {
            publisher.publish(EventSamples.orderCreated());
            throw new IllegalStateException("business step failed after publishing");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(outbox.count()).isZero();
    }

    @Test
    @DisplayName("publishing outside a transaction is a programming error and fails loudly")
    void publishingNeedsATransaction() {
        assertThatThrownBy(() -> publisher.publish(EventSamples.orderCreated()))
                .isInstanceOf(IllegalTransactionStateException.class);
        assertThat(outbox.count()).isZero();
    }

    @Test
    @DisplayName("the same event id cannot be queued twice")
    void eventIdIsUnique() {
        tx.executeWithoutResult(status -> publisher.publish(EventSamples.orderCreated()));

        assertThatThrownBy(() -> tx.executeWithoutResult(status -> publisher.publish(EventSamples.orderCreated())))
                .isInstanceOfAny(DataIntegrityViolationException.class, org.springframework.orm.jpa.JpaSystemException.class,
                        com.ecommerce.common.eventsourcing.EventSourcingException.class);
    }

    // ------------------------------------------------------------------ the relay

    @Test
    @DisplayName("the relay publishes with the aggregate as key and the logical type as header, then marks it published")
    @SuppressWarnings("unchecked")
    void relayPublishesAndMarksPublished() {
        kafkaAcknowledges();
        tx.executeWithoutResult(status -> publisher.publish(EventSamples.orderCreated(), "corr-1", "cause-1"));

        assertThat(relay.relayOnce()).isEqualTo(1);

        org.mockito.ArgumentCaptor<ProducerRecord<String, String>> sent = org.mockito.ArgumentCaptor.forClass(ProducerRecord.class);
        verify(kafka).send(sent.capture());
        ProducerRecord<String, String> record = sent.getValue();
        assertThat(record.topic()).isEqualTo(Topics.ORDER_CREATED);
        assertThat(record.key()).isEqualTo("42");
        assertThat(header(record, "__TypeId__")).isEqualTo("order.created");
        assertThat(header(record, "eventId")).isEqualTo(EventSamples.orderCreated().getEventId());
        assertThat(header(record, "correlationId")).isEqualTo("corr-1");
        assertThat(header(record, "causationId")).isEqualTo("cause-1");
        assertThat(outbox.findAll()).singleElement().satisfies(row -> {
            assertThat(row.getStatus()).isEqualTo(OutboxEvent.PUBLISHED);
            assertThat(row.getPublishedAt()).isNotNull();
        });
        assertThat(relay.relayOnce()).as("nothing left to send").isZero();
    }

    @Test
    @DisplayName("when Kafka does not acknowledge, the event stays pending and is retried later - never lost")
    void failedSendIsRetried() {
        kafkaFails();
        tx.executeWithoutResult(status -> publisher.publish(EventSamples.orderCreated()));

        assertThat(relay.relayOnce()).isZero();

        OutboxEvent row = outbox.findAll().get(0);
        assertThat(row.getStatus()).isEqualTo(OutboxEvent.PENDING);
        assertThat(row.getAttempts()).isEqualTo(1);
        assertThat(row.getLastError()).contains("broker down");
        assertThat(row.getNextAttemptAt()).isAfter(Instant.now());
        assertThat(relay.relayOnce()).as("not due yet: it backs off").isZero();

        // The broker comes back; once the backoff has elapsed the very same row is delivered.
        kafkaAcknowledges();
        row.setNextAttemptAt(Instant.now().minusSeconds(1));
        outbox.save(row);
        assertThat(relay.relayOnce()).isEqualTo(1);
        assertThat(outbox.findAll().get(0).getStatus()).isEqualTo(OutboxEvent.PUBLISHED);
    }

    @Test
    @DisplayName("backoff grows exponentially and is capped")
    void backoffIsExponentialAndCapped() {
        properties.setInitialBackoff(Duration.ofSeconds(1));
        properties.setMaxBackoff(Duration.ofSeconds(60));

        assertThat(relay.backoff(1)).isEqualTo(Duration.ofSeconds(1));
        assertThat(relay.backoff(2)).isEqualTo(Duration.ofSeconds(2));
        assertThat(relay.backoff(5)).isEqualTo(Duration.ofSeconds(16));
        assertThat(relay.backoff(30)).isEqualTo(Duration.ofSeconds(60));
    }

    @Test
    @DisplayName("after the maximum attempts an event is parked as DEAD for a human, not retried forever")
    void givesUpAsDead() {
        kafkaFails();
        properties.setMaxAttempts(2);
        tx.executeWithoutResult(status -> publisher.publish(EventSamples.orderCreated()));

        relay.relayOnce();
        OutboxEvent row = outbox.findAll().get(0);
        row.setNextAttemptAt(Instant.now().minusSeconds(1));
        outbox.save(row);
        relay.relayOnce();

        assertThat(outbox.findAll().get(0).getStatus()).as(this::dump).isEqualTo(OutboxEvent.DEAD);
        assertThat(outbox.countByStatus(OutboxEvent.DEAD)).isEqualTo(1);
    }

    @Test
    @DisplayName("events of one aggregate leave in order: the second waits for the first")
    void orderIsPreservedPerAggregate() {
        kafkaAcknowledges();
        tx.executeWithoutResult(status -> {
            publisher.publish(EventSamples.orderCreated());
            publisher.publish(EventSamples.orderCancelled());
        });

        assertThat(relay.relayOnce()).as("only the first of the aggregate in this batch").isEqualTo(1);
        assertThat(sentEventTypes()).containsExactly("order.created");
        assertThat(relay.relayOnce()).isEqualTo(1);
        assertThat(sentEventTypes()).containsExactly("order.created", "order.cancelled");
    }

    @Test
    @DisplayName("if the first event of an aggregate cannot be sent, later ones are not sent past it")
    void failedEventBlocksItsSuccessors() {
        kafkaFails();
        tx.executeWithoutResult(status -> {
            publisher.publish(EventSamples.orderCreated());
            publisher.publish(EventSamples.orderCancelled());
        });

        relay.relayOnce();
        kafkaAcknowledges();
        relay.relayOnce(); // the first is backing off, so the second must keep waiting

        assertThat(outbox.findAll()).extracting(OutboxEvent::getStatus).containsOnly(OutboxEvent.PENDING);
    }

    @Test
    @DisplayName("events of different aggregates are relayed together in one batch")
    void differentAggregatesShareABatch() {
        kafkaAcknowledges();
        tx.executeWithoutResult(status -> {
            publisher.publish(EventSamples.orderCreated());
            OrderCancelledEvent other = new OrderCancelledEvent(99L, 1L, "x");
            publisher.publish(other);
        });

        assertThat(relay.relayOnce()).isEqualTo(2);
    }

    @Test
    @DisplayName("two replicas relaying at once never send the same event twice")
    void concurrentRelaysSendEachEventOnce() throws Exception {
        Map<String, AtomicInteger> sends = new ConcurrentHashMap<>();
        when(kafka.send(any(ProducerRecord.class))).thenAnswer(invocation -> {
            ProducerRecord<String, String> record = invocation.getArgument(0);
            sends.computeIfAbsent(header(record, "eventId"), k -> new AtomicInteger()).incrementAndGet();
            Thread.sleep(5);
            return CompletableFuture.completedFuture((SendResult<String, String>) null);
        });
        int events = 60;
        tx.executeWithoutResult(status -> {
            for (int i = 0; i < events; i++) {
                OrderCancelledEvent event = new OrderCancelledEvent(1000L + i, 1L, "bulk");
                publisher.publish(event);
            }
        });
        properties.setBatchSize(10);
        OutboxRelay second = new OutboxRelay(outbox, kafka, properties, transactionManager, meters);

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        for (OutboxRelay replica : List.of(relay, second)) {
            pool.submit(() -> {
                start.await();
                while (replica.relayOnce() > 0) {
                    // keep draining
                }
                return null;
            });
        }
        start.countDown();
        pool.shutdown();
        assertThat(pool.awaitTermination(60, TimeUnit.SECONDS)).isTrue();
        while (relay.relayOnce() > 0) {
            // a replica may have stopped while the other still held the last rows locked
        }

        assertThat(sends).as(this::dump).hasSize(events);
        assertThat(sends.values()).allSatisfy(count -> assertThat(count.get()).isEqualTo(1));
        assertThat(outbox.countByStatus(OutboxEvent.PUBLISHED)).isEqualTo(events);
    }

    @Test
    @DisplayName("published rows are purged after their retention; pending ones never are")
    void purgesOnlyOldPublishedRows() {
        kafkaAcknowledges();
        tx.executeWithoutResult(status -> {
            publisher.publish(EventSamples.orderCreated());
            publisher.publish(new OrderCancelledEvent(7L, 1L, "pending one"));
        });
        relay.relayOnce();
        List<OutboxEvent> rows = outbox.findAll();
        OutboxEvent old = rows.get(0);
        old.setPublishedAt(Instant.now().minus(Duration.ofDays(2)));
        outbox.save(old);
        OutboxEvent pending = rows.get(1);
        pending.setStatus(OutboxEvent.PENDING);
        pending.setPublishedAt(null);
        outbox.save(pending);

        maintenance.purgePublished();

        assertThat(outbox.findAll()).extracting(OutboxEvent::getEventId).containsExactly(pending.getEventId());
    }

    private String dump() {
        return outbox.findAll().stream().map(o -> o.getId() + ":" + o.getStatus() + "/a" + o.getAttempts() + "/next=" + o.getNextAttemptAt() + "/" + o.getLastError())
                .collect(java.util.stream.Collectors.joining(" | "));
    }

    private List<String> sentEventTypes() {
        org.mockito.ArgumentCaptor<ProducerRecord<String, String>> captor = org.mockito.ArgumentCaptor.forClass(ProducerRecord.class);
        verify(kafka, org.mockito.Mockito.atLeast(1)).send(captor.capture());
        return captor.getAllValues().stream().map(r -> header(r, "eventType")).toList();
    }

    private static String header(ProducerRecord<String, String> record, String name) {
        return new String(record.headers().lastHeader(name).value(), StandardCharsets.UTF_8);
    }
}
