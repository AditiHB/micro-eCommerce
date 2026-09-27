package com.ecommerce.common.events;

import java.math.BigDecimal;

/**
 * Event published when a refund is successfully processed (compensating transaction completion).
 * Published by PaymentService after processing refund.
 */
public class RefundCompletedEvent extends DomainEvent {
    private Long orderId;
    private Long paymentId;
    private BigDecimal refundAmount;

    public RefundCompletedEvent() {
        super();
    }

    public RefundCompletedEvent(Long orderId, Long paymentId, BigDecimal refundAmount) {
        super(String.valueOf(orderId), "Payment");
        this.orderId = orderId;
        this.paymentId = paymentId;
        this.refundAmount = refundAmount;
    }

    public Long getOrderId() { return orderId; }
    public void setOrderId(Long orderId) { this.orderId = orderId; }
    public Long getPaymentId() { return paymentId; }
    public void setPaymentId(Long paymentId) { this.paymentId = paymentId; }
    public BigDecimal getRefundAmount() { return refundAmount; }
    public void setRefundAmount(BigDecimal refundAmount) { this.refundAmount = refundAmount; }
}
