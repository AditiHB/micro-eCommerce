package com.ecommerce.common.metrics;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * ApplicationMetrics is a plain @Component (not Spring Boot auto-configuration), and its
 * counters/timers are only populated once initialize() runs - normally triggered by
 * MetricsInitializer on ApplicationStartedEvent in a real application. This module has no
 * @SpringBootApplication class to bootstrap for a @SpringBootTest, so it's constructed and
 * initialized directly here against a real (in-memory) MeterRegistry.
 */
@DisplayName("ApplicationMetrics Unit Tests")
class ApplicationMetricsTest {

    private MeterRegistry meterRegistry;
    private ApplicationMetrics applicationMetrics;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        applicationMetrics = new ApplicationMetrics(meterRegistry);
        applicationMetrics.initialize();
    }

    @Test
    @DisplayName("Should record customer creation time")
    void testRecordCustomerCreationTime() {
        Timer.Sample sample = applicationMetrics.recordCustomerCreationTime();

        assertThat(sample).isNotNull();
    }

    @Test
    @DisplayName("Should record customer created metric")
    void testRecordCustomerCreated() {
        applicationMetrics.recordCustomerCreated();

        assertThat(meterRegistry.counter("ecommerce.customers.created").count()).isGreaterThanOrEqualTo(0);
    }

    @Test
    @DisplayName("Should stop customer creation timer")
    void testStopCustomerCreationTimer() {
        Timer.Sample sample = applicationMetrics.recordCustomerCreationTime();
        applicationMetrics.stopCustomerCreationTimer(sample);

        assertThat(sample).isNotNull();
    }

    @Test
    @DisplayName("Should throw when stopping with a null timer sample")
    void testNullTimerSample() {
        assertThatThrownBy(() -> applicationMetrics.stopCustomerCreationTimer(null))
            .isInstanceOf(NullPointerException.class);
    }
}
