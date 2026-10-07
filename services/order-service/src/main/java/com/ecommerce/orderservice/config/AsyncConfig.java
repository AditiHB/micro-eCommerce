package com.ecommerce.orderservice.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * Small, dedicated thread pools for this service's own fan-out points. Each is kept separate and purpose-sized
 * instead of sharing one general pool, so a burst on the background reaper can never eat into capacity the
 * request path needs, or vice versa.
 */
@Configuration
public class AsyncConfig {

    /**
     * For the independent remote calls a single order-placement request needs (see
     * {@code OrderPlacementService}). Only ever a couple of short blocking HTTP calls per request, each already
     * guarded by its own resilience4j timeout/circuit breaker, so a bounded queue is enough to shed load instead
     * of piling up requests behind a stuck downstream.
     */
    @Bean("remoteCallExecutor")
    public Executor remoteCallExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(8);
        executor.setMaxPoolSize(16);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("remote-call-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.initialize();
        return executor;
    }

    /**
     * For {@code OrderSagaDeadline}'s reaper pass: up to one batch (100) of independent per-order expirations,
     * each already its own transaction under optimistic locking. A background job, not a user request, so a
     * smaller pool and a queue deep enough to hold a full batch are enough - there is no latency SLA to protect,
     * only throughput, and a skipped order is simply retried on the next pass.
     */
    @Bean("sagaReaperExecutor")
    public Executor sagaReaperExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("saga-reaper-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.initialize();
        return executor;
    }
}
