package com.ecommerce.common.events;

/**
 * Event published when inventory is successfully reserved.
 * Includes product and quantity information for potential compensation.
 */
public class InventoryReservedEvent extends DomainEvent {
    private Long orderId;
    private String productId;
    private Integer quantity;

    public InventoryReservedEvent() {
        super();
    }

    public InventoryReservedEvent(Long orderId) {
        super(String.valueOf(orderId), "Inventory");
        this.orderId = orderId;
    }

    public InventoryReservedEvent(Long orderId, String productId, Integer quantity) {
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
