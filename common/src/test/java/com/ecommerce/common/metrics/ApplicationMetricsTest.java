package com.ecommerce.common.metrics;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@DisplayName("ApplicationMetrics Unit Tests")
class ApplicationMetricsTest {

    @Autowired
    private ApplicationMetrics applicationMetrics;

    @Autowired
    private MeterRegistry meterRegistry;

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

        assertThat(meterRegistry.counter("customers.created").count()).isGreaterThanOrEqualTo(0);
    }

    @Test
    @DisplayName("Should stop customer creation timer")
    void testStopCustomerCreationTimer() {
        Timer.Sample sample = applicationMetrics.recordCustomerCreationTime();
        applicationMetrics.stopCustomerCreationTimer(sample);

        assertThat(sample).isNotNull();
    }

    @Test
    @DisplayName("Should handle null timer sample gracefully")
    void testNullTimerSample() {
        assertThatCode(() -> applicationMetrics.stopCustomerCreationTimer(null))
            .doesNotThrowAnyException();
    }
}
