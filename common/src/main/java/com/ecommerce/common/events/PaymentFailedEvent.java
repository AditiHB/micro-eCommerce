package com.ecommerce.common.events;

public class PaymentFailedEvent extends DomainEvent {
    private Long orderId;

    public PaymentFailedEvent() {
        super();
    }

    public PaymentFailedEvent(Long orderId) {
        super(String.valueOf(orderId), "Order");
        this.orderId = orderId;
    }

    public Long getOrderId() { return orderId; }
    public void setOrderId(Long orderId) { this.orderId = orderId; }
}
