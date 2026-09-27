package com.ecommerce.common.enums;

public enum PaymentStatus {
    PENDING("Pending"),
    PROCESSING("Processing"),
    PROCESSED("Processed"),
    FAILED("Failed"),
    REFUNDED("Refunded");

    private final String displayName;

    PaymentStatus(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
