package com.ecommerce.productservice.config;

import com.ecommerce.productservice.event.InventoryReleasedEvent;
import com.ecommerce.productservice.event.InventoryReservedEvent;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;

import java.util.HashMap;
import java.util.Map;

@Configuration
@EnableKafka
public class KafkaConfig {

    @Bean
    public ConsumerFactory<String, InventoryReservedEvent> inventoryReservedConsumerFactory() {
        Map<String, Object> configProps = new HashMap<>();
        configProps.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092");
        configProps.put(ConsumerConfig.GROUP_ID_CONFIG, "product-group");
        configProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        configProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class);
        configProps.put(JsonDeserializer.VALUE_DEFAULT_TYPE, InventoryReservedEvent.class.getName());
        configProps.put(JsonDeserializer.TRUSTED_PACKAGES, "*");
        configProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        return new DefaultKafkaConsumerFactory<>(configProps, new StringDeserializer(),
                new JsonDeserializer<>(InventoryReservedEvent.class));
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, InventoryReservedEvent> inventoryReservedKafkaListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, InventoryReservedEvent> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(inventoryReservedConsumerFactory());
        factory.setConcurrency(3);
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL);
        return factory;
    }

    @Bean
    public ConsumerFactory<String, InventoryReleasedEvent> inventoryReleasedConsumerFactory() {
        Map<String, Object> configProps = new HashMap<>();
        configProps.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092");
        configProps.put(ConsumerConfig.GROUP_ID_CONFIG, "product-group");
        configProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        configProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class);
        configProps.put(JsonDeserializer.VALUE_DEFAULT_TYPE, InventoryReleasedEvent.class.getName());
        configProps.put(JsonDeserializer.TRUSTED_PACKAGES, "*");
        configProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        return new DefaultKafkaConsumerFactory<>(configProps, new StringDeserializer(),
                new JsonDeserializer<>(InventoryReleasedEvent.class));
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, InventoryReleasedEvent> inventoryReleasedKafkaListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, InventoryReleasedEvent> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(inventoryReleasedConsumerFactory());
        factory.setConcurrency(3);
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL);
        return factory;
    }

    @Bean(name = "kafkaListenerContainerFactory")
    public ConcurrentKafkaListenerContainerFactory<String, Object> kafkaListenerContainerFactory(ConsumerFactory<String, Object> consumerFactory) {
        ConcurrentKafkaListenerContainerFactory<String, Object> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);
        factory.setConcurrency(3);
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL);
        return factory;
    }
}
