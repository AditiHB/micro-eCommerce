package com.ecommerce.common.enums;

import java.util.EnumSet;
import java.util.Set;

/**
 * The lifecycle of a payment. Money only moves along these edges:
 *
 * <pre>
 *   PENDING --> AUTHORIZED --> CAPTURED --> REFUNDED
 *      |            |
 *      +------------+--> FAILED
 * </pre>
 *
 * A refund is only possible from CAPTURED (you cannot refund money that was never taken), and REFUNDED and
 * FAILED are terminal.
 */
public enum PaymentStatus {
    PENDING("Pending"),
    AUTHORIZED("Authorized"),
    CAPTURED("Captured"),
    FAILED("Failed"),
    REFUNDED("Refunded");

    private final String displayName;

    PaymentStatus(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public Set<PaymentStatus> allowedNext() {
        return switch (this) {
            case PENDING -> EnumSet.of(AUTHORIZED, FAILED);
            case AUTHORIZED -> EnumSet.of(CAPTURED, FAILED);
            case CAPTURED -> EnumSet.of(REFUNDED);
            case FAILED, REFUNDED -> EnumSet.noneOf(PaymentStatus.class);
        };
    }

    public boolean canTransitionTo(PaymentStatus next) {
        return allowedNext().contains(next);
    }
}
