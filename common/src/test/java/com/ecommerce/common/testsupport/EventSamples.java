package com.ecommerce.common.testsupport;

import com.ecommerce.common.events.DomainEvent;
import com.ecommerce.common.events.InventoryFailedEvent;
import com.ecommerce.common.events.InventoryReleasedEvent;
import com.ecommerce.common.events.InventoryReservedEvent;
import com.ecommerce.common.events.LineItem;
import com.ecommerce.common.events.OrderCancelledEvent;
import com.ecommerce.common.events.OrderCreatedEvent;
import com.ecommerce.common.events.PaymentFailedEvent;
import com.ecommerce.common.events.PaymentProcessedEvent;
import com.ecommerce.common.events.RefundCompletedEvent;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/** One fully-populated, deterministic instance of every event, shared by the contract tests of all modules. */
public final class EventSamples {

    public static final Instant OCCURRED_AT = Instant.parse("2026-01-01T10:15:30Z");

    private EventSamples() {
    }

    public static List<LineItem> lines() {
        return List.of(new LineItem("SKU-001", 2, new BigDecimal("79.99")), new LineItem("SKU-002", 1, new BigDecimal("12.99")));
    }

    public static OrderCreatedEvent orderCreated() {
        return fix(new OrderCreatedEvent(42L, 7L, lines(), new BigDecimal("172.97"), "USD"));
    }

    public static OrderCancelledEvent orderCancelled() {
        return fix(new OrderCancelledEvent(42L, 7L, "Payment declined"));
    }

    public static InventoryReservedEvent inventoryReserved() {
        return fix(new InventoryReservedEvent(42L, 7L, lines(), new BigDecimal("172.97"), "USD"));
    }

    public static InventoryFailedEvent inventoryFailed() {
        return fix(new InventoryFailedEvent(42L, 7L, "Insufficient stock for SKU-001"));
    }

    public static InventoryReleasedEvent inventoryReleased() {
        return fix(new InventoryReleasedEvent(42L, lines()));
    }

    public static PaymentProcessedEvent paymentProcessed() {
        return fix(new PaymentProcessedEvent(9L, 42L, 7L, new BigDecimal("172.97"), "USD"));
    }

    public static PaymentFailedEvent paymentFailed() {
        return fix(new PaymentFailedEvent(42L, 7L, "Card declined"));
    }

    public static RefundCompletedEvent refundCompleted() {
        return fix(new RefundCompletedEvent(42L, 9L, 7L, new BigDecimal("172.97"), "USD"));
    }

    // Variants for a particular order and event id, for tests that drive a handler with several events.

    public static OrderCreatedEvent orderCreated(long orderId, String eventId) {
        OrderCreatedEvent e = new OrderCreatedEvent(orderId, 7L, lines(), new BigDecimal("172.97"), "USD");
        e.setEventId(eventId);
        return e;
    }

    public static OrderCancelledEvent orderCancelled(long orderId, String eventId) {
        OrderCancelledEvent e = new OrderCancelledEvent(orderId, 7L, "Payment declined");
        e.setEventId(eventId);
        return e;
    }

    public static InventoryReservedEvent inventoryReserved(long orderId, String eventId) {
        InventoryReservedEvent e = new InventoryReservedEvent(orderId, 7L, lines(), new BigDecimal("172.97"), "USD");
        e.setEventId(eventId);
        return e;
    }

    public static InventoryFailedEvent inventoryFailed(long orderId, String eventId) {
        InventoryFailedEvent e = new InventoryFailedEvent(orderId, 7L, "Insufficient stock for SKU-001");
        e.setEventId(eventId);
        return e;
    }

    public static PaymentProcessedEvent paymentProcessed(long orderId, String eventId) {
        PaymentProcessedEvent e = new PaymentProcessedEvent(9L, orderId, 7L, new BigDecimal("172.97"), "USD");
        e.setEventId(eventId);
        return e;
    }

    public static PaymentFailedEvent paymentFailed(long orderId, String eventId) {
        PaymentFailedEvent e = new PaymentFailedEvent(orderId, 7L, "Card declined");
        e.setEventId(eventId);
        return e;
    }

    /** logical type name -> sample */
    public static Map<String, DomainEvent> all() {
        return Map.of(
                "order.created", orderCreated(),
                "order.cancelled", orderCancelled(),
                "inventory.reserved", inventoryReserved(),
                "inventory.failed", inventoryFailed(),
                "inventory.released", inventoryReleased(),
                "payment.processed", paymentProcessed(),
                "payment.failed", paymentFailed(),
                "refund.completed", refundCompleted());
    }

    private static <E extends DomainEvent> E fix(E event) {
        event.setEventId("evt-" + event.getEventType());
        event.setOccurredAt(OCCURRED_AT);
        return event;
    }
}
