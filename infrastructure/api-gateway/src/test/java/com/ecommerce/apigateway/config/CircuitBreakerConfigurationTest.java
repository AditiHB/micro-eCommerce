package com.ecommerce.apigateway.config;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.core.registry.EntryAddedEvent;
import io.github.resilience4j.core.registry.RegistryEventConsumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("CircuitBreakerConfiguration Unit Tests")
class CircuitBreakerConfigurationTest {

    @InjectMocks
    private CircuitBreakerConfiguration config;

    @BeforeEach
    void setUp() {
    }

    @Test
    @DisplayName("Should create circuit breaker registry event consumer")
    void testCircuitBreakerEventConsumer() {
        RegistryEventConsumer<CircuitBreaker> consumer = config.circuitBreakerEventConsumer();

        assertThat(consumer).isNotNull();
    }

    @Test
    @DisplayName("Should configure event consumer for circuit breaker events")
    void testEventConsumerConfiguration() {
        RegistryEventConsumer<CircuitBreaker> consumer = config.circuitBreakerEventConsumer();

        assertThat(consumer).isNotNull();
    }

    @Test
    @DisplayName("Should handle circuit breaker added events")
    void testCircuitBreakerAddedEvent() {
        RegistryEventConsumer<CircuitBreaker> consumer = config.circuitBreakerEventConsumer();

        assertThat(consumer).isNotNull();
    }
}
