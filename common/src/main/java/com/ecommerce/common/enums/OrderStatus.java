package com.ecommerce.common.enums;

public enum OrderStatus {
    PENDING("Pending"),
    INVENTORY_RESERVED("Inventory Reserved"),
    PAYMENT_PROCESSING("Payment Processing"),
    COMPLETED("Completed"),
    CANCELLED("Cancelled"),
    FAILED("Failed");

    private final String displayName;

    OrderStatus(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
