package com.ecommerce.common.events;

/**
 * Event published when inventory reservation is released (compensating transaction).
 * Triggered when payment fails or order is cancelled.
 */
public class InventoryReleasedEvent extends DomainEvent {
    private Long orderId;
    private String productId;
    private Integer quantity;

    public InventoryReleasedEvent() {
        super();
    }

    public InventoryReleasedEvent(Long orderId, String productId, Integer quantity) {
        super(String.valueOf(orderId), "Inventory");
        this.orderId = orderId;
        this.productId = productId;
        this.quantity = quantity;
    }

    public Long getOrderId() { return orderId; }
    public void setOrderId(Long orderId) { this.orderId = orderId; }
    public String getProductId() { return productId; }
    public void setProductId(String productId) { this.productId = productId; }
    public Integer getQuantity() { return quantity; }
    public void setQuantity(Integer quantity) { this.quantity = quantity; }
}
