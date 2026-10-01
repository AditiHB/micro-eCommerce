package com.ecommerce.common.enums;

/**
 * Types of customer notifications sent by the Notification Service.
 */
public enum NotificationType {
    ORDER_CREATED("Order Created"),
    PAYMENT_SUCCESS("Payment Successful"),
    PAYMENT_FAILED("Payment Failed");

    private final String displayName;

    NotificationType(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
