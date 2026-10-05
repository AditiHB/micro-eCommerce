package com.ecommerce.common.inbox;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;

/**
 * Makes an event consumer idempotent. Kafka (and the outbox relay, and a dead-letter replay) deliver at least
 * once, so the same event can arrive twice. A handler calls {@link #firstDelivery} as its first act, inside
 * its own database transaction: the claim row commits or rolls back together with the handler's effects, so a
 * handler that failed leaves nothing behind and is simply retried, while one that succeeded is never repeated.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class InboxService {

    /** Must outlast the longest time an event can still be redelivered (topic retention and DLQ replays). */
    static final Duration RETENTION = Duration.ofDays(14);

    private final ProcessedEventRepository repository;

    /**
     * @param consumer a name unique to this consumer of the event (its Kafka group works well)
     * @return true if this is the first time {@code consumer} sees {@code eventId} - go ahead and process it;
     *         false if it was already processed - skip it
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean firstDelivery(String consumer, String eventId) {
        boolean first = repository.claim(consumer, eventId, Instant.now()) == 1;
        if (!first) {
            log.info("Event {} was already processed by {} - skipping duplicate delivery", eventId, consumer);
        }
        return first;
    }

    @Scheduled(cron = "${ecommerce.inbox.cleanup-cron:0 37 3 * * *}")
    @Transactional
    public void purgeOld() {
        int deleted = repository.deleteProcessedBefore(Instant.now().minus(RETENTION));
        if (deleted > 0) {
            log.info("Purged {} processed-event rows older than {}", deleted, RETENTION);
        }
    }
}
