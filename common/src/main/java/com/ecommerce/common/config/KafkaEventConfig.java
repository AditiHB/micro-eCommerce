package com.ecommerce.common.config;

import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.config.KafkaListenerContainerFactory;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.*;
import org.springframework.kafka.listener.ConcurrentMessageListenerContainer;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.support.serializer.ErrorHandlingDeserializer;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.support.serializer.JsonSerializer;

import java.util.HashMap;
import java.util.Map;

@Configuration
@EnableKafka
public class KafkaEventConfig {

    @Value("${spring.kafka.bootstrap-servers:localhost:9092}")
    private String bootstrapServers;

    @Value("${kafka.event.replication-factor:1}")
    private short replicationFactor;

    @Value("${kafka.event.partitions:3}")
    private int partitions;

    @Value("${kafka.event.dlq-suffix:-dlq}")
    private String dlqSuffix;

    @Value("${spring.kafka.listener.auto-startup:${kafka.listener.auto-startup:true}}")
    private boolean listenerAutoStartup;

    // Fail fast instead of blocking the calling request thread: KafkaProducer.send() performs
    // synchronous metadata lookup before returning its Future, and blocks the caller for up to
    // max.block.ms if the broker is unreachable (default 60s). EventPublisher.publishEvent() is
    // called synchronously from request-handling code (e.g. OrderService.createOrder()), so a low
    // max.block.ms/request.timeout.ms means a Kafka outage surfaces as a quick EventPublishingException
    // (handled by the caller's own circuit breaker/retry) rather than a ~60s-per-request hang.
    @Value("${kafka.producer.max-block-ms:3000}")
    private int producerMaxBlockMs;

    @Value("${kafka.producer.request-timeout-ms:5000}")
    private int producerRequestTimeoutMs;

    @Value("${kafka.producer.delivery-timeout-ms:15000}")
    private int producerDeliveryTimeoutMs;

    @Value("${spring.kafka.admin.auto-create:true}")
    private boolean adminAutoCreate;

    @Bean
    public KafkaAdmin kafkaAdmin() {
        Map<String, Object> configs = new HashMap<>();
        configs.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        KafkaAdmin admin = new KafkaAdmin(configs);
        admin.setAutoCreate(adminAutoCreate);
        return admin;
    }

    @Bean
    public ProducerFactory<String, Object> producerFactory() {
        Map<String, Object> configProps = new HashMap<>();
        configProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        configProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        configProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        configProps.put(ProducerConfig.ACKS_CONFIG, "all");
        configProps.put(ProducerConfig.RETRIES_CONFIG, 3);
        configProps.put(ProducerConfig.RETRY_BACKOFF_MS_CONFIG, 1000);
        configProps.put(ProducerConfig.MAX_BLOCK_MS_CONFIG, producerMaxBlockMs);
        configProps.put(ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG, producerRequestTimeoutMs);
        configProps.put(ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG, producerDeliveryTimeoutMs);
        // Type headers ON (the default): the consumer side's JsonDeserializer has no usable
        // default type to fall back to (DomainEvent is abstract - every concrete *Event class
        // is a different shape on a different topic), so it relies entirely on this header to
        // know which concrete class to deserialize into. Turning this off makes every consumer
        // fail with "No type information in headers and no default type provided" and silently
        // never process a single event - this shared factory is used for every topic.
        return new DefaultKafkaProducerFactory<>(configProps);
    }

    @Bean
    public KafkaTemplate<String, Object> kafkaTemplate() {
        return new KafkaTemplate<>(producerFactory());
    }

    @Bean
    public ConsumerFactory<String, Object> consumerFactory() {
        Map<String, Object> configProps = new HashMap<>();
        configProps.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        configProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        configProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ErrorHandlingDeserializer.class);
        configProps.put(ErrorHandlingDeserializer.VALUE_DESERIALIZER_CLASS, JsonDeserializer.class.getName());
        configProps.put(JsonDeserializer.VALUE_DEFAULT_TYPE, "com.ecommerce.common.events.DomainEvent");
        configProps.put(JsonDeserializer.TRUSTED_PACKAGES, "*");
        configProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        configProps.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        configProps.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, 100);
        configProps.put(ConsumerConfig.SESSION_TIMEOUT_MS_CONFIG, 30000);
        return new DefaultKafkaConsumerFactory<>(configProps);
    }

    @Bean
    public KafkaListenerContainerFactory<ConcurrentMessageListenerContainer<String, Object>> kafkaListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, Object> factory =
            new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory());
        factory.setConcurrency(3);
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL);
        factory.setAutoStartup(listenerAutoStartup);
        return factory;
    }

    // DLQ topics carry a plain JSON string (the failed event, serialized by
    // DlqPublisher) rather than a typed DomainEvent - a dead-letter queue
    // needs to tolerate whatever got written there, including a payload
    // that doesn't cleanly deserialize. Deliberately separate producer/
    // consumer stack from the main event one above (plain String (de)
    // serializers, no type headers), matching DeadLetterQueueHandler's
    // own `@Payload String message` listener signature.

    @Bean
    public ProducerFactory<String, String> dlqProducerFactory() {
        Map<String, Object> configProps = new HashMap<>();
        configProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        configProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        configProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        configProps.put(ProducerConfig.ACKS_CONFIG, "all");
        configProps.put(ProducerConfig.MAX_BLOCK_MS_CONFIG, producerMaxBlockMs);
        configProps.put(ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG, producerRequestTimeoutMs);
        configProps.put(ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG, producerDeliveryTimeoutMs);
        return new DefaultKafkaProducerFactory<>(configProps);
    }

    @Bean
    public KafkaTemplate<String, String> dlqKafkaTemplate() {
        return new KafkaTemplate<>(dlqProducerFactory());
    }

    @Bean
    public ConsumerFactory<String, String> dlqConsumerFactory() {
        Map<String, Object> configProps = new HashMap<>();
        configProps.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        configProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        configProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        configProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        configProps.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        return new DefaultKafkaConsumerFactory<>(configProps);
    }

    @Bean("dlqKafkaListenerContainerFactory")
    public KafkaListenerContainerFactory<ConcurrentMessageListenerContainer<String, String>> dlqKafkaListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, String> factory =
            new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(dlqConsumerFactory());
        factory.setConcurrency(1);
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL);
        factory.setAutoStartup(listenerAutoStartup);
        return factory;
    }

    @Bean
    public NewTopic orderCreatedTopic() {
        return TopicBuilder.name("order-created")
            .partitions(partitions)
            .replicas(replicationFactor)
            .build();
    }

    @Bean
    public NewTopic orderCreatedDlqTopic() {
        return TopicBuilder.name("order-created" + dlqSuffix)
            .partitions(1)
            .replicas(replicationFactor)
            .build();
    }

    @Bean
    public NewTopic paymentProcessedTopic() {
        return TopicBuilder.name("payment-processed")
            .partitions(partitions)
            .replicas(replicationFactor)
            .build();
    }

    @Bean
    public NewTopic paymentProcessedDlqTopic() {
        return TopicBuilder.name("payment-processed" + dlqSuffix)
            .partitions(1)
            .replicas(replicationFactor)
            .build();
    }

    @Bean
    public NewTopic paymentFailedTopic() {
        return TopicBuilder.name("payment-failed")
            .partitions(partitions)
            .replicas(replicationFactor)
            .build();
    }

    @Bean
    public NewTopic paymentFailedDlqTopic() {
        return TopicBuilder.name("payment-failed" + dlqSuffix)
            .partitions(1)
            .replicas(replicationFactor)
            .build();
    }

    @Bean
    public NewTopic inventoryReservedTopic() {
        return TopicBuilder.name("inventory-reserved")
            .partitions(partitions)
            .replicas(replicationFactor)
            .build();
    }

    @Bean
    public NewTopic inventoryReservedDlqTopic() {
        return TopicBuilder.name("inventory-reserved" + dlqSuffix)
            .partitions(1)
            .replicas(replicationFactor)
            .build();
    }

    @Bean
    public NewTopic inventoryFailedTopic() {
        return TopicBuilder.name("inventory-failed")
            .partitions(partitions)
            .replicas(replicationFactor)
            .build();
    }

    @Bean
    public NewTopic inventoryFailedDlqTopic() {
        return TopicBuilder.name("inventory-failed" + dlqSuffix)
            .partitions(1)
            .replicas(replicationFactor)
            .build();
    }
}
