package com.ecommerce.common.events;

import java.math.BigDecimal;

public class PaymentProcessedEvent extends DomainEvent {
    private Long paymentId;
    private Long orderId;
    private BigDecimal amount;

    public PaymentProcessedEvent() {
        super();
    }

    public PaymentProcessedEvent(Long paymentId, Long orderId, BigDecimal amount) {
        super(String.valueOf(paymentId), "Payment");
        this.paymentId = paymentId;
        this.orderId = orderId;
        this.amount = amount;
    }

    public Long getPaymentId() { return paymentId; }
    public void setPaymentId(Long paymentId) { this.paymentId = paymentId; }

    public Long getOrderId() { return orderId; }
    public void setOrderId(Long orderId) { this.orderId = orderId; }

    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
}
