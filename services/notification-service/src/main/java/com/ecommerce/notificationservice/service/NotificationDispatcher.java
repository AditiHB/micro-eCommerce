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
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

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
    private final Executor notificationSendExecutor;

    public NotificationDispatcher(NotificationRepository repository, NotificationSender sender,
                                  NotificationProperties properties, PlatformTransactionManager transactionManager,
                                  ObjectProvider<MeterRegistry> meters,
                                  @Qualifier("notificationSendExecutor") Executor notificationSendExecutor) {
        this.repository = repository;
        this.sender = sender;
        this.properties = properties;
        this.transaction = new TransactionTemplate(transactionManager);
        this.meters = meters;
        this.notificationSendExecutor = notificationSendExecutor;
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

    /**
     * One pass over the notifications that are due; returns how many were handled. The blocking sends - each an
     * external mail/SMS/webhook call, the slowest I/O in this service - go out concurrently on
     * {@link #notificationSendExecutor}; every entity mutation then happens back here, on this single thread,
     * after all of them finish. That split matters: the {@link Notification} rows are Hibernate-managed by this
     * transaction's session, which is not thread-safe, so nothing but this thread may ever touch them.
     */
    public int dispatchOnce() {
        Integer handled = transaction.execute(status -> {
            List<Notification> due = repository.lockDue(Instant.now(), properties.getBatchSize());
            List<CompletableFuture<SendOutcome>> outcomes = due.stream()
                    .map(n -> submitSend(n.getRecipient(), n.getSubject(), n.getMessage()))
                    .toList();
            CompletableFuture.allOf(outcomes.toArray(new CompletableFuture[0])).join();
            for (int i = 0; i < due.size(); i++) {
                apply(due.get(i), outcomes.get(i).join());
            }
            return due.size();
        });
        return handled == null ? 0 : handled;
    }

    /** The result of one send attempt - never an exception, so it can be handed back across the executor safely. */
    private record SendOutcome(boolean success, String errorReason) {
        static SendOutcome ok() {
            return new SendOutcome(true, null);
        }

        static SendOutcome failure(String reason) {
            return new SendOutcome(false, reason);
        }
    }

    /** Dispatches one send to {@link #notificationSendExecutor}; a saturated pool is just another failed attempt. */
    private CompletableFuture<SendOutcome> submitSend(String recipient, String subject, String message) {
        try {
            return CompletableFuture.supplyAsync(() -> {
                try {
                    sender.send(recipient, subject, message);
                    return SendOutcome.ok();
                } catch (RuntimeException e) {
                    // NotificationDeliveryException is the expected failure; anything else from a sender is treated the same.
                    return SendOutcome.failure(
                            truncate(e instanceof NotificationDeliveryException ? e.getMessage() : e.getClass().getSimpleName() + ": " + e.getMessage()));
                }
            }, notificationSendExecutor);
        } catch (RejectedExecutionException e) {
            log.warn("notificationSendExecutor is saturated; this send will be recorded as failed and retried on backoff");
            return CompletableFuture.completedFuture(SendOutcome.failure("Too many notifications in flight; will retry"));
        }
    }

    /** Applies one send's outcome to its notification. Always runs on the transaction's own thread. */
    private void apply(Notification notification, SendOutcome outcome) {
        int attempt = notification.getAttempts() + 1;
        notification.setAttempts(attempt);
        if (outcome.success()) {
            notification.setStatus(NotificationStatus.SENT);
            notification.setErrorMessage(null);
            notification.setNextAttemptAt(null);
            log.info("Notification {} ({}) sent to {} for order {}", notification.getId(), notification.getType(),
                    notification.getRecipient(), notification.getOrderId());
        } else {
            notification.setErrorMessage(outcome.errorReason());
            if (attempt >= properties.getMaxAttempts()) {
                notification.setStatus(NotificationStatus.FAILED);
                notification.setNextAttemptAt(null);
                log.error("Notification {} failed permanently after {} attempts: {}", notification.getId(), attempt, outcome.errorReason());
            } else {
                notification.setNextAttemptAt(Instant.now().plus(backoff(attempt)));
                log.warn("Notification {} attempt {} failed ({}); will retry", notification.getId(), attempt, outcome.errorReason());
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
