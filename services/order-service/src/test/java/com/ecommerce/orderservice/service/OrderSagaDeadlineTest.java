package com.ecommerce.orderservice.service;

import com.ecommerce.orderservice.OrderRepository;
import com.ecommerce.orderservice.config.OrderProperties;
import com.ecommerce.orderservice.idempotency.IdempotencyRecordRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.PageRequest;

import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("OrderSagaDeadline")
class OrderSagaDeadlineTest {

    @Mock
    private OrderRepository orderRepository;
    @Mock
    private OrderService orderService;
    @Mock
    private IdempotencyRecordRepository idempotencyRepository;

    private final OrderProperties properties = new OrderProperties();

    private OrderSagaDeadline deadline(Executor executor) {
        return new OrderSagaDeadline(orderRepository, orderService, idempotencyRepository, properties, executor);
    }

    @Test
    @DisplayName("every stale order is expired, even if some fail")
    void expiresEveryStaleOrder() {
        when(orderRepository.findStaleOpenOrderIds(any(), any(), any(PageRequest.class)))
                .thenReturn(List.of(1L, 2L, 3L));
        when(orderService.expire(eq(1L), any(), any())).thenReturn(true);
        when(orderService.expire(eq(2L), any(), any())).thenThrow(new OptimisticLockingFailureException("raced"));
        when(orderService.expire(eq(3L), any(), any())).thenThrow(new IllegalStateException("boom"));

        // Synchronous executor: a failure on one order must not stop the others from being attempted.
        assertThatCode(() -> deadline(Runnable::run).cancelStaleOrders()).doesNotThrowAnyException();

        verify(orderService).expire(eq(1L), any(), any());
        verify(orderService).expire(eq(2L), any(), any());
        verify(orderService).expire(eq(3L), any(), any());
    }

    @Test
    @DisplayName("a saturated reaper executor skips that order instead of failing the pass")
    void skipsOrdersWhenExecutorIsSaturated() {
        when(orderRepository.findStaleOpenOrderIds(any(), any(), any(PageRequest.class))).thenReturn(List.of(1L));
        Executor rejecting = task -> {
            throw new RejectedExecutionException("pool saturated");
        };

        assertThatCode(() -> deadline(rejecting).cancelStaleOrders()).doesNotThrowAnyException();

        verifyNoInteractions(orderService);
    }

    @Test
    @DisplayName("the whole batch runs concurrently, not one order at a time")
    void runsConcurrently() {
        List<Long> ids = List.of(1L, 2L, 3L, 4L);
        when(orderRepository.findStaleOpenOrderIds(any(), any(), any(PageRequest.class))).thenReturn(ids);
        AtomicInteger inFlight = new AtomicInteger();
        AtomicInteger maxObserved = new AtomicInteger();
        when(orderService.expire(anyLong(), any(), any())).thenAnswer(invocation -> {
            maxObserved.updateAndGet(max -> Math.max(max, inFlight.incrementAndGet()));
            Thread.sleep(50);
            inFlight.decrementAndGet();
            return false;
        });
        ExecutorService pool = Executors.newFixedThreadPool(4);
        try {
            deadline(pool).cancelStaleOrders();
        } finally {
            pool.shutdown();
        }

        assertThat(maxObserved.get()).isGreaterThan(1);
    }
}
