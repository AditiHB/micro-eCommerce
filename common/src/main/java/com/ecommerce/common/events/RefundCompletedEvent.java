package com.ecommerce.common.events;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/** A captured payment was refunded through the processor. */
@Getter
@Setter
@NoArgsConstructor
@EventSchema(type = "refund.completed", topic = Topics.REFUND_COMPLETED)
public class RefundCompletedEvent extends DomainEvent {

    private Long orderId;
    private Long paymentId;
    private Long customerId;
    private BigDecimal refundAmount;
    private String currency;

    public RefundCompletedEvent(Long orderId, Long paymentId, Long customerId, BigDecimal refundAmount, String currency) {
        super(String.valueOf(orderId), "Payment");
        this.orderId = orderId;
        this.paymentId = paymentId;
        this.customerId = customerId;
        this.refundAmount = refundAmount;
        this.currency = currency;
    }
}
