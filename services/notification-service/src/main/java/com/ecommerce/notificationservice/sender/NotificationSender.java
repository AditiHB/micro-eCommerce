package com.ecommerce.notificationservice.sender;

/**
 * Delivers a notification through a concrete channel (email, log, SMS, ...).
 * Implementations should throw a {@link NotificationDeliveryException} on
 * failure so the caller can record it on the Notification record.
 */
public interface NotificationSender {

    /**
     * @param recipient the destination address (e.g. email address)
     * @param subject   short summary line
     * @param message   full notification body
     */
    void send(String recipient, String subject, String message);
}
