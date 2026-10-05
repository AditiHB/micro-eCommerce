package com.ecommerce.common.outbox;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Hands outbox rows to Kafka and marks them published only after the broker has acknowledged them
 * ({@code acks=all}). A crash or a broker outage therefore never loses an event: the row is still pending
 * and is simply sent again - delivery is at-least-once, and consumers de-duplicate on the event id.
 *
 * <p>Each poll runs in one database transaction that holds the due rows locked ({@code SKIP LOCKED}), so any
 * number of replicas can run this relay side by side. A failed event backs off exponentially and, after
 * {@code maxAttempts}, is parked as DEAD where an alert picks it up.
 */
@Component
@ConditionalOnProperty(prefix = "ecommerce.outbox", name = "relay-enabled", havingValue = "true", matchIfMissing = true)
@Slf4j
public class OutboxRelay {

    private final OutboxRepository repository;
    private final KafkaTemplate<String, String> kafka;
    private final OutboxProperties properties;
    private final TransactionTemplate transaction;
    private final Counter published;
    private final Counter failed;

    public OutboxRelay(OutboxRepository repository,
                       KafkaTemplate<String, String> outboxKafkaTemplate,
                       OutboxProperties properties,
                       PlatformTransactionManager transactionManager,
                       ObjectProvider<MeterRegistry> meters) {
        this.repository = repository;
        this.kafka = outboxKafkaTemplate;
        this.properties = properties;
        this.transaction = new TransactionTemplate(transactionManager);
        MeterRegistry registry = meters.getIfAvailable();
        this.published = registry == null ? null
                : Counter.builder("ecommerce.outbox.published").description("Events handed to Kafka").register(registry);
        this.failed = registry == null ? null
                : Counter.builder("ecommerce.outbox.failures").description("Failed attempts to hand an event to Kafka").register(registry);
    }

    @Scheduled(fixedDelayString = "${ecommerce.outbox.poll-interval:PT0.25S}")
    public void poll() {
        try {
            int delivered;
            do {
                delivered = relayOnce();
            } while (delivered >= properties.getBatchSize());
        } catch (RuntimeException e) {
            // A database hiccup must not kill the scheduler thread; the next poll tries again.
            log.error("Outbox relay poll failed", e);
        }
    }

    /** Relays one batch; returns how many events were acknowledged by the broker. */
    public int relayOnce() {
        Integer delivered = transaction.execute(status ->
                deliver(repository.lockDueBatch(Instant.now(), properties.getBatchSize())));
        return delivered == null ? 0 : delivered;
    }

    private int deliver(List<OutboxEvent> batch) {
        List<OutboxEvent> inFlight = new ArrayList<>(batch.size());
        List<CompletableFuture<SendResult<String, String>>> futures = new ArrayList<>(batch.size());

        for (OutboxEvent event : batch) {
            try {
                futures.add(kafka.send(toRecord(event)));
                inFlight.add(event);
            } catch (RuntimeException e) {
                recordFailure(event, e);
                // The broker is unreachable (metadata lookup timed out): do not spend another timeout on
                // every remaining event - they stay pending and are picked up by the next poll.
                break;
            }
        }

        int delivered = 0;
        for (int i = 0; i < inFlight.size(); i++) {
            OutboxEvent event = inFlight.get(i);
            try {
                futures.get(i).get(properties.getSendTimeout().toMillis(), TimeUnit.MILLISECONDS);
                event.setStatus(OutboxEvent.PUBLISHED);
                event.setPublishedAt(Instant.now());
                event.setAttempts(event.getAttempts() + 1);
                event.setLastError(null);
                delivered++;
                if (published != null) {
                    published.increment();
                }
                log.debug("Published {} ({}) to {}", event.getEventType(), event.getEventId(), event.getTopic());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                recordFailure(event, e);
            } catch (ExecutionException | TimeoutException e) {
                recordFailure(event, e.getCause() != null ? e.getCause() : e);
            }
        }
        return delivered;
    }

    private void recordFailure(OutboxEvent event, Throwable cause) {
        int attempts = event.getAttempts() + 1;
        event.setAttempts(attempts);
        event.setLastError(truncate(cause.getClass().getSimpleName() + ": " + cause.getMessage()));
        if (failed != null) {
            failed.increment();
        }
        if (attempts >= properties.getMaxAttempts()) {
            event.setStatus(OutboxEvent.DEAD);
            log.error("Outbox event {} ({}) gave up after {} attempts - parked as DEAD: {}",
                    event.getEventType(), event.getEventId(), attempts, event.getLastError());
        } else {
            event.setNextAttemptAt(Instant.now().plus(backoff(attempts)));
            log.warn("Could not publish {} ({}) to {} (attempt {}): {}. Will retry.",
                    event.getEventType(), event.getEventId(), event.getTopic(), attempts, event.getLastError());
        }
    }

    Duration backoff(int attempts) {
        long millis = properties.getInitialBackoff().toMillis() << Math.min(Math.max(attempts - 1, 0), 20);
        return Duration.ofMillis(Math.min(millis, properties.getMaxBackoff().toMillis()));
    }

    private static ProducerRecord<String, String> toRecord(OutboxEvent event) {
        ProducerRecord<String, String> record =
                new ProducerRecord<>(event.getTopic(), null, event.getMessageKey(), event.getPayload());
        // The logical type name is the only type information on the wire - never a Java class name.
        header(record, "__TypeId__", event.getEventType());
        header(record, "eventId", event.getEventId());
        header(record, "eventType", event.getEventType());
        header(record, "aggregateId", event.getAggregateId());
        header(record, "aggregateType", event.getAggregateType());
        header(record, "correlationId", event.getCorrelationId());
        header(record, "causationId", event.getCausationId());
        return record;
    }

    private static void header(ProducerRecord<String, String> record, String name, String value) {
        if (value != null) {
            record.headers().add(name, value.getBytes(StandardCharsets.UTF_8));
        }
    }

    private static String truncate(String text) {
        return text.length() <= 1000 ? text : text.substring(0, 1000);
    }
}
