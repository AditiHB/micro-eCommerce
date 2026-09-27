package com.ecommerce.common.events;

public class InventoryFailedEvent extends DomainEvent {
    private Long orderId;

    public InventoryFailedEvent() {
        super();
    }

    public InventoryFailedEvent(Long orderId) {
        super(String.valueOf(orderId), "Inventory");
        this.orderId = orderId;
    }

    public Long getOrderId() { return orderId; }
    public void setOrderId(Long orderId) { this.orderId = orderId; }
}
