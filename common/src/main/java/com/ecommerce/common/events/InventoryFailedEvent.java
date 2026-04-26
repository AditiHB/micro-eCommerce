package com.ecommerce.common.events;

public class InventoryFailedEvent {
    private Long orderId;

    public InventoryFailedEvent() {}

    public InventoryFailedEvent(Long orderId) {
        this.orderId = orderId;
    }

    public Long getOrderId() { return orderId; }
    public void setOrderId(Long orderId) { this.orderId = orderId; }
}
