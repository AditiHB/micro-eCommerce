package com.ecommerce.common.config;

import com.ecommerce.common.events.EventCatalog;
import com.ecommerce.common.events.Topics;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.config.TopicConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collection;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("KafkaEventConfig")
class KafkaEventConfigTest {

    private KafkaEventConfig config;

    @BeforeEach
    void setUp() {
        config = new KafkaEventConfig();
        // @Value fields are populated by Spring in a running app; set the production defaults by hand here.
        ReflectionTestUtils.setField(config, "bootstrapServers", "localhost:9092");
        ReflectionTestUtils.setField(config, "replicationFactor", (short) 1);
        ReflectionTestUtils.setField(config, "minInsyncReplicas", 1);
        ReflectionTestUtils.setField(config, "partitions", 3);
        ReflectionTestUtils.setField(config, "dlqPartitions", 1);
        ReflectionTestUtils.setField(config, "dlqRetentionMs", 1_209_600_000L);
        ReflectionTestUtils.setField(config, "retryMaxAttempts", 5);
        ReflectionTestUtils.setField(config, "retryInitialIntervalMs", 1000L);
        ReflectionTestUtils.setField(config, "retryMultiplier", 2.0);
        ReflectionTestUtils.setField(config, "retryMaxIntervalMs", 30_000L);
        ReflectionTestUtils.setField(config, "concurrency", 3);
    }

    @SuppressWarnings("unchecked")
    private Collection<NewTopic> newTopics() {
        return (Collection<NewTopic>) ReflectionTestUtils.invokeMethod(config.eventTopics(), "getNewTopics");
    }

    private Map<String, NewTopic> topicsByName() {
        Collection<NewTopic> topics = newTopics();
        return topics.stream().collect(Collectors.toMap(NewTopic::name, t -> t));
    }

    @Test
    @DisplayName("declares a topic and a dead-letter topic for every event type - none rely on auto-creation")
    void declaresEveryTopic() {
        Map<String, NewTopic> topics = topicsByName();

        for (String topic : EventCatalog.topics()) {
            assertThat(topics).containsKeys(topic, topic + Topics.DLQ_SUFFIX);
        }
        // the two the old config forgot
        assertThat(topics).containsKeys(Topics.ORDER_CANCELLED, Topics.REFUND_COMPLETED, Topics.INVENTORY_RELEASED);
        assertThat(topics.get(Topics.ORDER_CREATED).numPartitions()).isEqualTo(3);
        assertThat(topics.get(Topics.ORDER_CREATED + Topics.DLQ_SUFFIX).numPartitions()).isEqualTo(1);
    }

    @Test
    @DisplayName("a replicated cluster gets RF 3 and min.insync.replicas 2 on every topic, dead letters included")
    void replicatedTopics() {
        ReflectionTestUtils.setField(config, "replicationFactor", (short) 3);
        ReflectionTestUtils.setField(config, "minInsyncReplicas", 2);

        assertThat(topicsByName().values()).allSatisfy(topic -> {
            assertThat(topic.replicationFactor()).isEqualTo((short) 3);
            assertThat(topic.configs()).containsEntry(TopicConfig.MIN_IN_SYNC_REPLICAS_CONFIG, "2");
        });
    }

    @Test
    @DisplayName("dead-letter topics keep messages for two weeks so an operator has time to act")
    void dlqRetention() {
        assertThat(topicsByName().get(Topics.PAYMENT_FAILED + Topics.DLQ_SUFFIX).configs())
                .containsEntry(TopicConfig.RETENTION_MS_CONFIG, "1209600000");
    }

    @Test
    @DisplayName("consumers are told exactly which logical names map to which classes")
    void typeMappingIsAnAllowList() {
        String mapping = EventCatalog.typeMapping();

        assertThat(mapping).contains("order.created:com.ecommerce.common.events.OrderCreatedEvent")
                .contains("payment.processed:com.ecommerce.common.events.PaymentProcessedEvent");
    }

    @Test
    @DisplayName("builds the error handler and the recoverer that dead-letters to <topic>-dlq")
    void errorHandlingBuilds() {
        DeadLetterPublishingRecoverer recoverer = config.deadLetterRecoverer();

        assertThat(recoverer).isNotNull();
        assertThat(config.kafkaErrorHandler(recoverer)).isNotNull();
        // Retry timing, dead-lettering and the "never retry a poison message" rule are proven against a real
        // broker in KafkaDeliveryIntegrationTest.
    }
}
