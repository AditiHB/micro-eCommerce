package com.ecommerce.common.events;

public class InventoryReservedEvent extends DomainEvent {
    private Long orderId;

    public InventoryReservedEvent() {
        super();
    }

    public InventoryReservedEvent(Long orderId) {
        super(String.valueOf(orderId), "Inventory");
        this.orderId = orderId;
    }

    public Long getOrderId() { return orderId; }
    public void setOrderId(Long orderId) { this.orderId = orderId; }
}
