package com.ecommerce.common.events;

public class PaymentProcessedEvent {
    private Long orderId;

    public PaymentProcessedEvent() {}

    public PaymentProcessedEvent(Long orderId) {
        this.orderId = orderId;
    }

    public Long getOrderId() { return orderId; }
    public void setOrderId(Long orderId) { this.orderId = orderId; }
}
