package com.ecommerce.notificationservice.sender;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Default notification channel: logs the notification instead of actually
 * dispatching it. Safe to run out of the box without any mail server
 * configured - useful for local development and tests. Enabled whenever
 * `notification.channel` is "log" (the default).
 */
@Component
@Slf4j
@ConditionalOnProperty(name = "notification.channel", havingValue = "log", matchIfMissing = true)
public class LoggingNotificationSender implements NotificationSender {

    @Override
    public void send(String recipient, String subject, String message) {
        log.info("✉ [SIMULATED EMAIL] To: {} | Subject: {} | Message: {}", recipient, subject, message);
    }
}
