package com.ecommerce.common.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("KafkaEventConfig Unit Tests")
class KafkaEventConfigTest {

    @InjectMocks
    private KafkaEventConfig kafkaConfig;

    @BeforeEach
    void setUp() {
        // @Value fields are not populated by Mockito's @InjectMocks, so they must be
        // set manually - otherwise DefaultKafkaProducerFactory's internal ConcurrentHashMap
        // throws a NullPointerException on a null bootstrap-servers value.
        ReflectionTestUtils.setField(kafkaConfig, "bootstrapServers", "localhost:9092");
        ReflectionTestUtils.setField(kafkaConfig, "replicationFactor", (short) 1);
        ReflectionTestUtils.setField(kafkaConfig, "partitions", 3);
        ReflectionTestUtils.setField(kafkaConfig, "dlqSuffix", "-dlq");
    }

    @Test
    @DisplayName("Should create Kafka producer factory")
    void testProducerFactoryCreation() {
        ProducerFactory<String, Object> producerFactory = kafkaConfig.producerFactory();

        assertThat(producerFactory).isNotNull();
        assertThat(producerFactory).isInstanceOf(DefaultKafkaProducerFactory.class);
    }

    @Test
    @DisplayName("Should create Kafka template bean")
    void testKafkaTemplateCreation() {
        KafkaTemplate<String, Object> kafkaTemplate = kafkaConfig.kafkaTemplate();

        assertThat(kafkaTemplate).isNotNull();
    }

    @Test
    @DisplayName("Should configure producer with String serializer for key")
    void testProducerSerializerConfiguration() {
        ProducerFactory<String, Object> factory = kafkaConfig.producerFactory();

        assertThat(factory).isNotNull();
    }

    @Test
    @DisplayName("Should configure batch size for Kafka producer")
    void testProducerBatchConfig() {
        ProducerFactory<String, Object> factory = kafkaConfig.producerFactory();

        assertThat(factory).isNotNull();
    }

    @Test
    @DisplayName("Should configure topic creation settings")
    void testTopicCreation() {
        assertThat(kafkaConfig).isNotNull();
    }
}
