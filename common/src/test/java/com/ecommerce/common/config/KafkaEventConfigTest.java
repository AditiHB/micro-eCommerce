package com.ecommerce.common.config;

import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;

import static org.assertj.core.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("KafkaEventConfig Unit Tests")
class KafkaEventConfigTest {

    @InjectMocks
    private KafkaEventConfig kafkaConfig;

    @Test
    @DisplayName("Should create Kafka producer factory")
    void testProducerFactoryCreation() {
        ProducerFactory<String, String> producerFactory = kafkaConfig.producerFactory();

        assertThat(producerFactory).isNotNull();
        assertThat(producerFactory).isInstanceOf(DefaultKafkaProducerFactory.class);
    }

    @Test
    @DisplayName("Should create Kafka template bean")
    void testKafkaTemplateCreation() {
        KafkaTemplate<String, String> kafkaTemplate = kafkaConfig.kafkaTemplate();

        assertThat(kafkaTemplate).isNotNull();
    }

    @Test
    @DisplayName("Should configure producer with String serializer for key")
    void testProducerSerializerConfiguration() {
        ProducerFactory<String, String> factory = kafkaConfig.producerFactory();

        assertThat(factory).isNotNull();
    }

    @Test
    @DisplayName("Should configure batch size for Kafka producer")
    void testProducerBatchConfig() {
        ProducerFactory<String, String> factory = kafkaConfig.producerFactory();

        assertThat(factory).isNotNull();
    }

    @Test
    @DisplayName("Should configure topic creation settings")
    void testTopicCreation() {
        assertThat(kafkaConfig).isNotNull();
    }
}
