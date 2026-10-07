package com.ecommerce.orderservice.service;

import com.ecommerce.common.enums.OrderStatus;
import com.ecommerce.orderservice.OrderRepository;
import com.ecommerce.orderservice.config.OrderProperties;
import com.ecommerce.orderservice.idempotency.IdempotencyRecordRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

/**
 * The saga's safety net. If an order has been in flight for longer than {@code ecommerce.orders.saga-timeout} - an
 * event was lost for good, a service was down too long, a message sits in a dead-letter queue - it is cancelled,
 * and the cancellation triggers every compensation. Without this an order could sit PENDING forever, holding
 * stock and possibly money.
 *
 * <p>Safe to run on every replica: each order is handled in its own transaction under optimistic locking, so if
 * two replicas pick the same order one simply loses the race.
 */
@Component
@ConditionalOnProperty(prefix = "ecommerce.orders", name = "reaper-enabled", havingValue = "true", matchIfMissing = true)
@Slf4j
public class OrderSagaDeadline {

    private static final int BATCH = 100;

    private final OrderRepository orderRepository;
    private final OrderService orderService;
    private final IdempotencyRecordRepository idempotencyRepository;
    private final OrderProperties properties;
    private final Executor sagaReaperExecutor;

    public OrderSagaDeadline(OrderRepository orderRepository, OrderService orderService,
                             IdempotencyRecordRepository idempotencyRepository, OrderProperties properties,
                             @Qualifier("sagaReaperExecutor") Executor sagaReaperExecutor) {
        this.orderRepository = orderRepository;
        this.orderService = orderService;
        this.idempotencyRepository = idempotencyRepository;
        this.properties = properties;
        this.sagaReaperExecutor = sagaReaperExecutor;
    }

    /**
     * Each stale order is independent - its own row, its own transaction, already safe under optimistic locking
     * even across replicas running this same pass concurrently (see the class javadoc) - so the batch is fanned
     * out across {@link #sagaReaperExecutor} instead of expired one at a time. A pass over a full batch of 100
     * is now bound by the slowest single expiry instead of the sum of all of them.
     */
    @Scheduled(fixedDelayString = "${ecommerce.orders.reaper-interval:PT30S}")
    public void cancelStaleOrders() {
        LocalDateTime cutoff = LocalDateTime.now().minus(properties.getSagaTimeout());
        List<Long> stale = orderRepository.findStaleOpenOrderIds(OrderStatus.open(), cutoff, PageRequest.of(0, BATCH));
        List<CompletableFuture<Void>> futures = new ArrayList<>(stale.size());
        for (Long id : stale) {
            try {
                futures.add(CompletableFuture.runAsync(() -> expireOne(id, cutoff), sagaReaperExecutor));
            } catch (RejectedExecutionException e) {
                // sagaReaperExecutor is saturated: leave this order be. It is still stale, so the next pass
                // (in reaper-interval) will pick it straight back up - nothing is lost, only delayed.
                log.warn("sagaReaperExecutor is saturated; order {} will be retried on the next reaper pass", id);
            }
        }
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
    }

    private void expireOne(Long id, LocalDateTime cutoff) {
        try {
            if (orderService.expire(id, cutoff, "Saga timed out: not completed within " + properties.getSagaTimeout())) {
                log.warn("Order {} exceeded the saga deadline of {} and was cancelled", id, properties.getSagaTimeout());
            }
        } catch (OptimisticLockingFailureException e) {
            log.debug("Order {} changed while being expired - leaving it to the saga", id);
        } catch (RuntimeException e) {
            log.error("Could not expire order {}", id, e);
        }
    }

    @Scheduled(cron = "${ecommerce.orders.idempotency-cleanup-cron:0 17 * * * *}")
    @Transactional
    public void purgeIdempotencyKeys() {
        int deleted = idempotencyRepository.deleteOlderThan(Instant.now().minus(properties.getIdempotencyRetention()));
        if (deleted > 0) {
            log.info("Purged {} expired idempotency keys", deleted);
        }
    }
}
