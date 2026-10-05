package com.ecommerce.notificationservice.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/** Delivery tuning ({@code ecommerce.notifications.*}). */
@Getter
@Setter
@ConfigurationProperties(prefix = "ecommerce.notifications")
public class NotificationProperties {

    /** Switch the dispatcher off (tests). Notifications are still recorded. */
    private boolean dispatcherEnabled = true;

    /** How often the dispatcher looks for notifications that are due. */
    private Duration dispatchInterval = Duration.ofSeconds(1);

    /** Most notifications handled per pass. */
    private int batchSize = 25;

    /** After this many failed attempts a notification is marked FAILED for good. */
    private int maxAttempts = 5;

    /** Wait before the second attempt; doubles each time up to {@link #maxBackoff}. */
    private Duration initialBackoff = Duration.ofSeconds(5);

    private Duration maxBackoff = Duration.ofMinutes(10);
}
