package com.ecommerce.common.events;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * An order was cancelled - by the customer, by back office, by a failed saga step or by the saga deadline.
 * This is the single trigger for every compensation: inventory releases its reservation and payment refunds
 * a captured charge. Both are idempotent, so the order service may publish it again if a late saga event
 * shows that a compensation was missed.
 */
@Getter
@Setter
@NoArgsConstructor
@EventSchema(type = "order.cancelled", topic = Topics.ORDER_CANCELLED)
public class OrderCancelledEvent extends DomainEvent {

    private Long orderId;
    private Long customerId;
    private String reason;

    public OrderCancelledEvent(Long orderId, Long customerId, String reason) {
        super(String.valueOf(orderId), "Order");
        this.orderId = orderId;
        this.customerId = customerId;
        this.reason = reason;
    }
}
