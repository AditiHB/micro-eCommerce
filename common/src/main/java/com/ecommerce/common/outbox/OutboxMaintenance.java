package com.ecommerce.common.outbox;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;

/**
 * Housekeeping and observability for the outbox: deletes published rows past their retention and exposes the
 * backlog as metrics, which the alert rules in {@code monitoring/alerts.yml} watch.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OutboxMaintenance {

    private final OutboxRepository repository;
    private final OutboxProperties properties;
    private final ObjectProvider<MeterRegistry> meters;

    @PostConstruct
    void registerGauges() {
        MeterRegistry registry = meters.getIfAvailable();
        if (registry == null) {
            return;
        }
        Gauge.builder("ecommerce.outbox.pending", this, m -> m.safe(() -> m.repository.countByStatus(OutboxEvent.PENDING)).doubleValue())
                .description("Events written but not yet acknowledged by Kafka")
                .register(registry);
        Gauge.builder("ecommerce.outbox.dead", this, m -> m.safe(() -> m.repository.countByStatus(OutboxEvent.DEAD)).doubleValue())
                .description("Events that exhausted their delivery attempts and need a human")
                .register(registry);
        Gauge.builder("ecommerce.outbox.oldest.pending.age.seconds", this, m -> m.safe(() ->
                        m.repository.oldestPendingCreatedAt()
                                .map(oldest -> (double) Duration.between(oldest, Instant.now()).toSeconds())
                                .orElse(0d)).doubleValue())
                .description("Age of the oldest event still waiting to be published")
                .register(registry);
    }

    @Scheduled(cron = "${ecommerce.outbox.cleanup-cron:0 7 * * * *}")
    @Transactional
    public void purgePublished() {
        int deleted = repository.deletePublishedBefore(Instant.now().minus(properties.getRetention()));
        if (deleted > 0) {
            log.info("Purged {} published outbox rows older than {}", deleted, properties.getRetention());
        }
    }

    private Number safe(java.util.function.Supplier<? extends Number> supplier) {
        try {
            return supplier.get();
        } catch (RuntimeException e) {
            return Double.NaN; // database briefly unavailable: report "unknown", never fail the scrape
        }
    }
}
