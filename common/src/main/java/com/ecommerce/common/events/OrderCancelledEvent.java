package com.ecommerce.common.events;

/**
 * Event published when an order is cancelled (triggers compensating transactions).
 * Published by OrderService when a failure event is received.
 */
public class OrderCancelledEvent extends DomainEvent {
    private Long orderId;
    private String reason;

    public OrderCancelledEvent() {
        super();
    }

    public OrderCancelledEvent(Long orderId, String reason) {
        super(String.valueOf(orderId), "Order");
        this.orderId = orderId;
        this.reason = reason;
    }

    public Long getOrderId() { return orderId; }
    public void setOrderId(Long orderId) { this.orderId = orderId; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
}
