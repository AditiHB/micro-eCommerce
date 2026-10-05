package com.ecommerce.notificationservice.service;

import com.ecommerce.common.enums.NotificationStatus;
import com.ecommerce.notificationservice.Notification;
import com.ecommerce.notificationservice.NotificationRepository;
import com.ecommerce.notificationservice.config.NotificationProperties;
import com.ecommerce.notificationservice.sender.NotificationDeliveryException;
import com.ecommerce.notificationservice.sender.NotificationSender;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Delivers the notifications that were recorded. Because delivery is separate from recording, a mail-server outage
 * can neither lose a notification (it stays PENDING and is retried with exponential backoff) nor stall the event
 * stream (the Kafka listener finished long ago). After the configured number of failed attempts it is marked FAILED
 * for good, with the last error.
 *
 * <p>Runs on every replica: due rows are locked with {@code SKIP LOCKED}, so none is sent twice.
 */
@Component
@ConditionalOnProperty(prefix = "ecommerce.notifications", name = "dispatcher-enabled", havingValue = "true", matchIfMissing = true)
@Slf4j
public class NotificationDispatcher {

    private final NotificationRepository repository;
    private final NotificationSender sender;
    private final NotificationProperties properties;
    private final TransactionTemplate transaction;
    private final ObjectProvider<MeterRegistry> meters;

    public NotificationDispatcher(NotificationRepository repository, NotificationSender sender,
                                  NotificationProperties properties, PlatformTransactionManager transactionManager,
                                  ObjectProvider<MeterRegistry> meters) {
        this.repository = repository;
        this.sender = sender;
        this.properties = properties;
        this.transaction = new TransactionTemplate(transactionManager);
        this.meters = meters;
    }

    @PostConstruct
    void registerMetrics() {
        MeterRegistry registry = meters.getIfAvailable();
        if (registry != null) {
            Gauge.builder("ecommerce.notifications.pending", repository, r -> {
                try {
                    return (double) r.countByStatus(NotificationStatus.PENDING);
                } catch (RuntimeException e) {
                    return Double.NaN;
                }
            }).description("Notifications recorded but not yet delivered").register(registry);
        }
    }

    @Scheduled(fixedDelayString = "${ecommerce.notifications.dispatch-interval:PT1S}")
    public void dispatch() {
        try {
            int handled;
            do {
                handled = dispatchOnce();
            } while (handled >= properties.getBatchSize());
        } catch (RuntimeException e) {
            log.error("Notification dispatch pass failed", e);
        }
    }

    /** One pass over the notifications that are due; returns how many were handled. */
    public int dispatchOnce() {
        Integer handled = transaction.execute(status -> {
            List<Notification> due = repository.lockDue(Instant.now(), properties.getBatchSize());
            due.forEach(this::deliver);
            return due.size();
        });
        return handled == null ? 0 : handled;
    }

    private void deliver(Notification notification) {
        int attempt = notification.getAttempts() + 1;
        notification.setAttempts(attempt);
        try {
            sender.send(notification.getRecipient(), notification.getSubject(), notification.getMessage());
            notification.setStatus(NotificationStatus.SENT);
            notification.setErrorMessage(null);
            notification.setNextAttemptAt(null);
            log.info("Notification {} ({}) sent to {} for order {}", notification.getId(), notification.getType(),
                    notification.getRecipient(), notification.getOrderId());
        } catch (RuntimeException e) {
            // NotificationDeliveryException is the expected failure; anything else from a sender is treated the same.
            String reason = truncate(e instanceof NotificationDeliveryException ? e.getMessage() : e.getClass().getSimpleName() + ": " + e.getMessage());
            notification.setErrorMessage(reason);
            if (attempt >= properties.getMaxAttempts()) {
                notification.setStatus(NotificationStatus.FAILED);
                notification.setNextAttemptAt(null);
                log.error("Notification {} failed permanently after {} attempts: {}", notification.getId(), attempt, reason);
            } else {
                notification.setNextAttemptAt(Instant.now().plus(backoff(attempt)));
                log.warn("Notification {} attempt {} failed ({}); will retry", notification.getId(), attempt, reason);
            }
        }
    }

    Duration backoff(int attempt) {
        long millis = properties.getInitialBackoff().toMillis() << Math.min(Math.max(attempt - 1, 0), 20);
        return Duration.ofMillis(Math.min(millis, properties.getMaxBackoff().toMillis()));
    }

    private static String truncate(String text) {
        return text == null ? null : text.length() <= 500 ? text : text.substring(0, 500);
    }
}
