package com.ecommerce.customerservice.config;

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
     * For {@code CustomerMetricsRecorder}: recording a technical KPI after a write. The HTTP response never
     * depends on it, and losing a queued one on shutdown is harmless (it is only a metrics data point), so
     * unlike the executors elsewhere in this codebase this one does not wait for in-flight tasks to finish
     * before shutting down.
     */
    @Bean("customerMetricsExecutor")
    public Executor customerMetricsExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("customer-metrics-");
        executor.setWaitForTasksToCompleteOnShutdown(false);
        executor.initialize();
        return executor;
    }
}
