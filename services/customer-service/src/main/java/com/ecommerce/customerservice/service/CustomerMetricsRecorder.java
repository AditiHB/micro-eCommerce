package com.ecommerce.customerservice.service;

import com.ecommerce.common.metrics.ApplicationMetrics;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * Records customer-lifecycle technical KPIs off the caller's thread: the HTTP response never depends on a
 * counter increment or a timer stop, so there is no reason to make a request wait for them. A separate bean,
 * not a private method on {@link CustomerService}: Spring's {@code @Async} proxy only intercepts calls that
 * arrive through another bean, so a call from within the same class would bypass it and run synchronously.
 */
@Component
@RequiredArgsConstructor
public class CustomerMetricsRecorder {

    private final ApplicationMetrics applicationMetrics;

    @Async("customerMetricsExecutor")
    public void recordCreated(Timer.Sample sample) {
        applicationMetrics.recordCustomerCreated();
        applicationMetrics.stopCustomerCreationTimer(sample);
    }

    @Async("customerMetricsExecutor")
    public void recordUpdated(Timer.Sample sample) {
        applicationMetrics.stopCustomerCreationTimer(sample);
    }
}
