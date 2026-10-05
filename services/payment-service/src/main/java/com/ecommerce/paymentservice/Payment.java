package com.ecommerce.paymentservice;

import com.ecommerce.common.enums.PaymentStatus;
import com.ecommerce.paymentservice.exception.InvalidPaymentTransitionException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * A payment for one order (one payment per order, enforced by a unique constraint). Money only moves along the
 * edges of {@link PaymentStatus}, and only through the methods here - there is no status setter - so a refund of
 * money that was never captured, or a second refund, is impossible by construction.
 */
@Entity
@Table(name = "payments")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
@EqualsAndHashCode(of = "id")
public class Payment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull(message = "Order ID cannot be null")
    @Column(nullable = false, unique = true)
    private Long orderId;

    private Long customerId;

    @NotNull(message = "Amount cannot be null")
    @DecimalMin(value = "0.01", message = "Amount must be greater than 0")
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;

    @NotNull
    @Column(nullable = false, length = 3)
    private String currency;

    @Setter(AccessLevel.NONE)
    @NotNull(message = "Status cannot be null")
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentStatus status;

    /** The processor's id for the last operation (authorization, capture or refund). */
    @Column(length = 100)
    private String processorReference;

    @Column(length = 500)
    private String failureReason;

    /** Optimistic lock and the resource's ETag. */
    @Version
    private Long version;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    /** A new payment, not yet submitted to the processor. */
    public static Payment pending(Long orderId, Long customerId, BigDecimal amount, String currency) {
        return Payment.builder().orderId(orderId).customerId(customerId).amount(amount).currency(currency)
                .status(PaymentStatus.PENDING).build();
    }

    public void authorized(String reference) {
        move(PaymentStatus.AUTHORIZED);
        this.processorReference = reference;
    }

    public void captured(String reference) {
        move(PaymentStatus.CAPTURED);
        this.processorReference = reference;
    }

    public void failed(String reason) {
        move(PaymentStatus.FAILED);
        this.failureReason = reason == null ? null : reason.substring(0, Math.min(reason.length(), 500));
    }

    /** Refuses early (409) - before the processor is called - if this payment's money cannot be given back. */
    public void requireRefundable() {
        if (!status.canTransitionTo(PaymentStatus.REFUNDED)) {
            throw new InvalidPaymentTransitionException(id, status, PaymentStatus.REFUNDED);
        }
    }

    public void refunded(String reference) {
        move(PaymentStatus.REFUNDED);
        this.processorReference = reference;
    }

    private void move(PaymentStatus next) {
        if (!status.canTransitionTo(next)) {
            throw new InvalidPaymentTransitionException(id, status, next);
        }
        this.status = next;
    }
}
