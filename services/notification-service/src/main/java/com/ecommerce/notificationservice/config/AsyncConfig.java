package com.ecommerce.notificationservice.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * A small, dedicated pool for fanning out one dispatch pass's notification sends (see
 * {@code NotificationDispatcher}). Each send is an external mail/SMS/webhook call - the slowest I/O in this
 * service - so a batch of up to {@code batchSize} (25 by default) is sent concurrently instead of one at a time.
 * Sized a little above the default batch size so a full batch fits without queueing in the common case.
 */
@Configuration
public class AsyncConfig {

    @Bean("notificationSendExecutor")
    public Executor notificationSendExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(8);
        executor.setMaxPoolSize(32);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("notification-send-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.initialize();
        return executor;
    }
}
