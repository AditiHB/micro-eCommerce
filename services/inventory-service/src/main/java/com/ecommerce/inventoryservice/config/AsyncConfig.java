package com.ecommerce.inventoryservice.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * Enables Spring's {@code @Async} for this service's fire-and-forget side work: a caller never waits for or
 * combines these results, unlike the fan-out-then-join {@code CompletableFuture} patterns elsewhere in this
 * codebase (order-service, notification-service), so the simpler annotation-driven model fits here instead.
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    /**
     * For {@code ProductCacheEvictor}: a cache eviction after a product write commits. Trivial, fast, local
     * work, so a small pool is enough - and losing a queued one on shutdown is harmless (the entry is simply
     * stale until the next write or its TTL expires), so unlike the executors elsewhere in this codebase this
     * one does not wait for in-flight tasks to finish before shutting down.
     */
    @Bean("cacheEvictionExecutor")
    public Executor cacheEvictionExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("cache-evict-");
        executor.setWaitForTasksToCompleteOnShutdown(false);
        executor.initialize();
        return executor;
    }
}
