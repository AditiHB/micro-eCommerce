package com.ecommerce.common.events;

/**
 * Topic names and consumer groups of the saga, in one place so a typo cannot silently create a new topic
 * (the broker topic list in {@code KafkaEventConfig} is generated from {@link EventCatalog}).
 */
public final class Topics {

    public static final String ORDER_CREATED = "order-created";
    public static final String ORDER_CANCELLED = "order-cancelled";
    public static final String INVENTORY_RESERVED = "inventory-reserved";
    public static final String INVENTORY_FAILED = "inventory-failed";
    public static final String INVENTORY_RELEASED = "inventory-released";
    public static final String PAYMENT_PROCESSED = "payment-processed";
    public static final String PAYMENT_FAILED = "payment-failed";
    public static final String REFUND_COMPLETED = "refund-completed";
    public static final String PRODUCT_EVENTS = "product-events";

    /** Suffix of the dead-letter topic of every topic above: {@code <topic>-dlq}. */
    public static final String DLQ_SUFFIX = "-dlq";

    public static final String GROUP_ORDER = "order-group";
    public static final String GROUP_INVENTORY = "inventory-group";
    public static final String GROUP_PAYMENT = "payment-group";
    public static final String GROUP_NOTIFICATION = "notification-group";

    private Topics() {
    }
}
