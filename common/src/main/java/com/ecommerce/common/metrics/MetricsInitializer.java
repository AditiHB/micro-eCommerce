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

    @EventListener(ApplicationStartedEvent.class)
    public void initializeMetrics() {
        log.info("Initializing application metrics");
        applicationMetrics.initialize();
        log.info("Application metrics initialized successfully");
    }
}
