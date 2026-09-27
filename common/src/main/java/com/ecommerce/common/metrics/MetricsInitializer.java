package com.ecommerce.common.metrics;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationStartedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class MetricsInitializer {

    private final ApplicationMetrics applicationMetrics;
    private final BusinessMetrics businessMetrics;

    @EventListener(ApplicationStartedEvent.class)
    public void initializeMetrics() {
        log.info("Initializing application and business metrics");
        applicationMetrics.initialize();
        businessMetrics.initialize();
        log.info("All metrics initialized successfully");
    }
}
