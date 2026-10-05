package com.ecommerce.common.events;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.List;

/** Stock for every line of the order is now held. Passes the order's priced total on to payment. */
@Getter
@Setter
@NoArgsConstructor
@EventSchema(type = "inventory.reserved", topic = Topics.INVENTORY_RESERVED)
public class InventoryReservedEvent extends DomainEvent {

    private Long orderId;
    private Long customerId;
    private List<LineItem> lines;
    private BigDecimal totalAmount;
    private String currency;

    public InventoryReservedEvent(Long orderId, Long customerId, List<LineItem> lines, BigDecimal totalAmount, String currency) {
        super(String.valueOf(orderId), "Inventory");
        this.orderId = orderId;
        this.customerId = customerId;
        this.lines = lines;
        this.totalAmount = totalAmount;
        this.currency = currency;
    }
}
