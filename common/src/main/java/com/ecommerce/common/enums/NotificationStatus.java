package com.ecommerce.common.enums;

/**
 * Delivery status of a customer notification.
 */
public enum NotificationStatus {
    PENDING("Pending"),
    SENT("Sent"),
    FAILED("Failed");

    private final String displayName;

    NotificationStatus(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
