package com.ecommerce.common.testsupport;

import org.testcontainers.kafka.KafkaContainer;

/** One real Kafka broker (KRaft, as in production) for the tests that exercise actual delivery. */
public final class SharedKafka {

    private static final KafkaContainer KAFKA = new KafkaContainer("apache/kafka:3.8.0");

    static {
        KAFKA.start();
    }

    private SharedKafka() {
    }

    public static String bootstrapServers() {
        return KAFKA.getBootstrapServers();
    }
}
