package com.ecommerce.common.enums;

import java.util.EnumSet;
import java.util.Set;

/**
 * The lifecycle of an order, with the transitions that are legal - an order can only ever move along these
 * edges, whoever asks (a Kafka event, a customer, back office or the saga deadline):
 *
 * <pre>
 *   PENDING --> INVENTORY_RESERVED --> PAYMENT_PROCESSING --> COMPLETED
 *      |               |                      |
 *      +---------------+----------------------+--> CANCELLED      (compensations are triggered by this)
 *      +---------------+----------------------+--> FAILED
 * </pre>
 *
 * {@code PENDING -> COMPLETED} is also legal: the saga's events travel on different topics and may be consumed
 * in either order, so "payment processed" can be seen before "inventory reserved". COMPLETED, CANCELLED and
 * FAILED are terminal: nothing leaves them.
 */
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

    /** The statuses an order in this status may move to. */
    public Set<OrderStatus> allowedNext() {
        return switch (this) {
            case PENDING -> EnumSet.of(INVENTORY_RESERVED, PAYMENT_PROCESSING, COMPLETED, CANCELLED, FAILED);
            case INVENTORY_RESERVED -> EnumSet.of(PAYMENT_PROCESSING, COMPLETED, CANCELLED, FAILED);
            case PAYMENT_PROCESSING -> EnumSet.of(COMPLETED, CANCELLED, FAILED);
            case COMPLETED, CANCELLED, FAILED -> EnumSet.noneOf(OrderStatus.class);
        };
    }

    public boolean canTransitionTo(OrderStatus next) {
        return allowedNext().contains(next);
    }

    public boolean isTerminal() {
        return allowedNext().isEmpty();
    }

    /** The statuses an order is still moving through: the ones the saga deadline watches. */
    public static Set<OrderStatus> open() {
        return EnumSet.of(PENDING, INVENTORY_RESERVED, PAYMENT_PROCESSING);
    }
}
