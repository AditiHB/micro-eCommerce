package com.ecommerce.common.events;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** The payment processor declined the charge (a business outcome, not an error). */
@Getter
@Setter
@NoArgsConstructor
@EventSchema(type = "payment.failed", topic = Topics.PAYMENT_FAILED)
public class PaymentFailedEvent extends DomainEvent {

    private Long orderId;
    private Long customerId;
    private String reason;

    public PaymentFailedEvent(Long orderId, Long customerId, String reason) {
        super(String.valueOf(orderId), "Payment");
        this.orderId = orderId;
        this.customerId = customerId;
        this.reason = reason;
    }
}
