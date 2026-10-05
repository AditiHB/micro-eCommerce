package com.ecommerce.common.events;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.List;

/**
 * An order was accepted. Carries everything the rest of the saga needs - the priced lines and the total -
 * so inventory can reserve and payment can charge without asking order-service anything.
 */
@Getter
@Setter
@NoArgsConstructor
@EventSchema(type = "order.created", topic = Topics.ORDER_CREATED)
public class OrderCreatedEvent extends DomainEvent {

    private Long orderId;
    private Long customerId;
    private List<LineItem> lines;
    private BigDecimal totalAmount;
    private String currency;

    public OrderCreatedEvent(Long orderId, Long customerId, List<LineItem> lines, BigDecimal totalAmount, String currency) {
        super(String.valueOf(orderId), "Order");
        this.orderId = orderId;
        this.customerId = customerId;
        this.lines = lines;
        this.totalAmount = totalAmount;
        this.currency = currency;
    }
}
