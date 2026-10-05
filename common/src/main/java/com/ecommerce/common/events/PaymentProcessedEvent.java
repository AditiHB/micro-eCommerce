package com.ecommerce.common.events;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/** The order's total was captured by the payment processor. */
@Getter
@Setter
@NoArgsConstructor
@EventSchema(type = "payment.processed", topic = Topics.PAYMENT_PROCESSED)
public class PaymentProcessedEvent extends DomainEvent {

    private Long paymentId;
    private Long orderId;
    private Long customerId;
    private BigDecimal amount;
    private String currency;

    public PaymentProcessedEvent(Long paymentId, Long orderId, Long customerId, BigDecimal amount, String currency) {
        super(String.valueOf(orderId), "Payment");
        this.paymentId = paymentId;
        this.orderId = orderId;
        this.customerId = customerId;
        this.amount = amount;
        this.currency = currency;
    }
}
