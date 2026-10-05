package com.ecommerce.common.events;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/** A reservation was given back to stock (compensation for a cancelled order). */
@Getter
@Setter
@NoArgsConstructor
@EventSchema(type = "inventory.released", topic = Topics.INVENTORY_RELEASED)
public class InventoryReleasedEvent extends DomainEvent {

    private Long orderId;
    private List<LineItem> lines;

    public InventoryReleasedEvent(Long orderId, List<LineItem> lines) {
        super(String.valueOf(orderId), "Inventory");
        this.orderId = orderId;
        this.lines = lines;
    }
}
