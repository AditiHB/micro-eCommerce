package com.ecommerce.notificationservice.sender;

/**
 * Raised when a {@link NotificationSender} fails to deliver a notification.
 */
public class NotificationDeliveryException extends RuntimeException {
    public NotificationDeliveryException(String message, Throwable cause) {
        super(message, cause);
    }

    public NotificationDeliveryException(String message) {
        super(message);
    }
}
