package com.ecommerce.common.events;

import java.math.BigDecimal;

/**
 * Event published when a refund is initiated (compensating transaction).
 * Triggered when order is cancelled or payment processing fails.
 */
public class RefundInitiatedEvent extends DomainEvent {
    private Long orderId;
    private Long paymentId;
    private BigDecimal refundAmount;
    private String reason;

    public RefundInitiatedEvent() {
        super();
    }

    public RefundInitiatedEvent(Long orderId, Long paymentId, BigDecimal refundAmount, String reason) {
        super(String.valueOf(orderId), "Payment");
        this.orderId = orderId;
        this.paymentId = paymentId;
        this.refundAmount = refundAmount;
        this.reason = reason;
    }

    public Long getOrderId() { return orderId; }
    public void setOrderId(Long orderId) { this.orderId = orderId; }
    public Long getPaymentId() { return paymentId; }
    public void setPaymentId(Long paymentId) { this.paymentId = paymentId; }
    public BigDecimal getRefundAmount() { return refundAmount; }
    public void setRefundAmount(BigDecimal refundAmount) { this.refundAmount = refundAmount; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
}
