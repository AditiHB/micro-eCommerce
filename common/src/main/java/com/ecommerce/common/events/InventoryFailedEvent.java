package com.ecommerce.common.events;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Stock could not be reserved for the whole order (nothing was reserved - reservation is all or nothing). */
@Getter
@Setter
@NoArgsConstructor
@EventSchema(type = "inventory.failed", topic = Topics.INVENTORY_FAILED)
public class InventoryFailedEvent extends DomainEvent {

    private Long orderId;
    private Long customerId;
    private String reason;

    public InventoryFailedEvent(Long orderId, Long customerId, String reason) {
        super(String.valueOf(orderId), "Inventory");
        this.orderId = orderId;
        this.customerId = customerId;
        this.reason = reason;
    }
}
