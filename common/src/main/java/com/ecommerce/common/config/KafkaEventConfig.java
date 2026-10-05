package com.ecommerce.common.config;

import com.ecommerce.common.events.EventCatalog;
import com.ecommerce.common.events.Topics;
import com.ecommerce.common.exception.NonRetryableEventException;
import com.ecommerce.common.messaging.EventJsonSerializer;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.config.TopicConfig;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.ExponentialBackOffWithMaxRetries;
import org.springframework.kafka.support.serializer.ErrorHandlingDeserializer;
import org.springframework.kafka.support.serializer.JsonDeserializer;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Kafka wiring for the saga: how events are consumed, how failures are retried and dead-lettered, and which
 * topics exist.
 *
 * <p><b>Consuming.</b> Events are JSON; the type header carries a <em>logical</em> name that is mapped to a class
 * through {@link EventCatalog} (an allow-list - no Java class names on the wire, no {@code "*"} trusted
 * packages). Offsets are committed per record, after the listener returned.
 *
 * <p><b>Failing.</b> A listener never catches-and-acks. It throws, and the {@link DefaultErrorHandler} retries
 * with exponential backoff (a transient lock conflict or database blip heals itself); only when the attempts
 * are used up, or the failure can never succeed (unreadable message), is the record published to
 * {@code <topic>-dlq} and the partition moves on. {@code DeadLetterQueueHandler} then parks it for an operator.
 *
 * <p><b>Producing.</b> Business code does not produce to Kafka at all - it writes the outbox, and the relay uses
 * {@link #outboxKafkaTemplate()}: idempotent, {@code acks=all}.
 */
@Configuration
@EnableKafka
public class KafkaEventConfig {

    @Value("${spring.kafka.bootstrap-servers:localhost:9092}")
    private String bootstrapServers;

    @Value("${kafka.event.replication-factor:1}")
    private short replicationFactor;

    /** With replication factor 3 set this to 2: a write is acknowledged only once two replicas have it. */
    @Value("${kafka.event.min-insync-replicas:1}")
    private int minInsyncReplicas;

    @Value("${kafka.event.partitions:3}")
    private int partitions;

    @Value("${kafka.event.dlq-partitions:1}")
    private int dlqPartitions;

    @Value("${kafka.event.dlq-retention-ms:1209600000}")
    private long dlqRetentionMs;

    @Value("${spring.kafka.listener.auto-startup:${kafka.listener.auto-startup:true}}")
    private boolean listenerAutoStartup;

    @Value("${kafka.consumer.concurrency:3}")
    private int concurrency;

    /** Total delivery attempts per record (first try included) before it is dead-lettered. */
    @Value("${kafka.consumer.retry.max-attempts:5}")
    private int retryMaxAttempts;

    @Value("${kafka.consumer.retry.initial-interval-ms:1000}")
    private long retryInitialIntervalMs;

    @Value("${kafka.consumer.retry.multiplier:2.0}")
    private double retryMultiplier;

    @Value("${kafka.consumer.retry.max-interval-ms:30000}")
    private long retryMaxIntervalMs;

    // The relay's sends block the polling thread only for these bounds, so a broker outage shows up as a
    // quick, retried failure rather than a hung scheduler.
    @Value("${kafka.producer.max-block-ms:3000}")
    private int producerMaxBlockMs;

    @Value("${kafka.producer.request-timeout-ms:5000}")
    private int producerRequestTimeoutMs;

    @Value("${kafka.producer.delivery-timeout-ms:15000}")
    private int producerDeliveryTimeoutMs;

    @Value("${spring.kafka.admin.auto-create:true}")
    private boolean adminAutoCreate;

    // ------------------------------------------------------------------ topics

    @Bean
    public KafkaAdmin kafkaAdmin() {
        Map<String, Object> configs = new HashMap<>();
        configs.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        KafkaAdmin admin = new KafkaAdmin(configs);
        admin.setAutoCreate(adminAutoCreate);
        return admin;
    }

    /**
     * Every event topic and its dead-letter topic, generated from {@link EventCatalog} so a new event type can
     * never be published to a topic nobody declared (and so never relies on broker auto-creation).
     */
    @Bean
    public KafkaAdmin.NewTopics eventTopics() {
        java.util.List<NewTopic> topics = new java.util.ArrayList<>();
        for (String topic : EventCatalog.topics()) {
            topics.add(new NewTopic(topic, partitions, replicationFactor)
                    .configs(Map.of(TopicConfig.MIN_IN_SYNC_REPLICAS_CONFIG, String.valueOf(minInsyncReplicas))));
            topics.add(new NewTopic(topic + Topics.DLQ_SUFFIX, dlqPartitions, replicationFactor)
                    .configs(Map.of(
                            TopicConfig.MIN_IN_SYNC_REPLICAS_CONFIG, String.valueOf(minInsyncReplicas),
                            TopicConfig.RETENTION_MS_CONFIG, String.valueOf(dlqRetentionMs))));
        }
        return new KafkaAdmin.NewTopics(topics.toArray(NewTopic[]::new));
    }

    // --------------------------------------------------------------- producing

    private Map<String, Object> baseProducerProps() {
        Map<String, Object> props = new HashMap<>();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.ACKS_CONFIG, "all");
        props.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        props.put(ProducerConfig.MAX_BLOCK_MS_CONFIG, producerMaxBlockMs);
        props.put(ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG, producerRequestTimeoutMs);
        props.put(ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG, producerDeliveryTimeoutMs);
        return props;
    }

    /** The only producer business code ends up using, through the outbox relay and dead-letter replays. */
    @Bean
    public ProducerFactory<String, String> outboxProducerFactory() {
        Map<String, Object> props = baseProducerProps();
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        return new DefaultKafkaProducerFactory<>(props);
    }

    @Bean
    public KafkaTemplate<String, String> outboxKafkaTemplate() {
        return new KafkaTemplate<>(outboxProducerFactory());
    }

    private KafkaTemplate<String, byte[]> deadLetterBytesTemplate() {
        Map<String, Object> props = baseProducerProps();
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, ByteArraySerializer.class);
        return new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(props));
    }

    private KafkaTemplate<String, Object> deadLetterEventTemplate() {
        Map<String, Object> props = baseProducerProps();
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, EventJsonSerializer.class);
        return new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(props));
    }

    // --------------------------------------------------------------- consuming

    @Bean
    public ConsumerFactory<String, Object> consumerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ErrorHandlingDeserializer.class);
        props.put(ErrorHandlingDeserializer.VALUE_DESERIALIZER_CLASS, JsonDeserializer.class);
        // Logical type name -> class, from the @EventSchema registry. Only these classes can ever be built.
        props.put(JsonDeserializer.TYPE_MAPPINGS, EventCatalog.typeMapping());
        props.put(JsonDeserializer.TRUSTED_PACKAGES, "com.ecommerce.common.events");
        props.put(JsonDeserializer.USE_TYPE_INFO_HEADERS, true);
        // Keep the type header on the record: if it is dead-lettered, a replay needs it.
        props.put(JsonDeserializer.REMOVE_TYPE_INFO_HEADERS, false);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        props.put(ConsumerConfig.ISOLATION_LEVEL_CONFIG, "read_committed");
        props.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, 100);
        props.put(ConsumerConfig.SESSION_TIMEOUT_MS_CONFIG, 30000);
        return new DefaultKafkaConsumerFactory<>(props);
    }

    /**
     * After the retries are used up the record goes to {@code <topic>-dlq}, byte for byte if it could not even be
     * deserialized. The original topic, partition, offset, consumer group and exception ride along as headers.
     */
    @Bean
    public DeadLetterPublishingRecoverer deadLetterRecoverer() {
        Map<Class<?>, KafkaOperations<?, ?>> templates = new LinkedHashMap<>();
        templates.put(byte[].class, deadLetterBytesTemplate());
        templates.put(Object.class, deadLetterEventTemplate());
        return new DeadLetterPublishingRecoverer(templates,
                (record, ex) -> new TopicPartition(record.topic() + Topics.DLQ_SUFFIX, -1));
    }

    /** Retry with exponential backoff, then dead-letter. Failures that can never succeed skip the retries. */
    @Bean
    public DefaultErrorHandler kafkaErrorHandler(DeadLetterPublishingRecoverer recoverer) {
        ExponentialBackOffWithMaxRetries backOff = new ExponentialBackOffWithMaxRetries(Math.max(retryMaxAttempts - 1, 0));
        backOff.setInitialInterval(retryInitialIntervalMs);
        backOff.setMultiplier(retryMultiplier);
        backOff.setMaxInterval(retryMaxIntervalMs);

        DefaultErrorHandler handler = new DefaultErrorHandler(recoverer, backOff);
        handler.addNotRetryableExceptions(NonRetryableEventException.class);
        handler.setCommitRecovered(true);
        return handler;
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, Object> kafkaListenerContainerFactory(
            DefaultErrorHandler kafkaErrorHandler) {
        ConcurrentKafkaListenerContainerFactory<String, Object> factory = new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory());
        factory.setConcurrency(concurrency);
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.RECORD);
        factory.setCommonErrorHandler(kafkaErrorHandler);
        factory.setAutoStartup(listenerAutoStartup);
        return factory;
    }

    // ------------------------------------------------------- dead-letter consuming

    // DLQ topics carry plain text; this stack is deliberately separate (String deserializers, no type
    // headers) so a message that does not deserialize cleanly can still be read, parked and inspected.

    @Bean
    public ConsumerFactory<String, String> dlqConsumerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        return new DefaultKafkaConsumerFactory<>(props);
    }

    @Bean("dlqKafkaListenerContainerFactory")
    public ConcurrentKafkaListenerContainerFactory<String, String> dlqKafkaListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, String> factory = new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(dlqConsumerFactory());
        factory.setConcurrency(1);
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.RECORD);
        // If parking itself keeps failing (database down) keep trying for a while; the message stays in Kafka.
        factory.setCommonErrorHandler(new DefaultErrorHandler(new org.springframework.util.backoff.FixedBackOff(2000L, 30L)));
        factory.setAutoStartup(listenerAutoStartup);
        return factory;
    }
}
