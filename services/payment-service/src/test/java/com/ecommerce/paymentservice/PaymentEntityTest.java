package com.ecommerce.paymentservice;

import com.ecommerce.common.enums.PaymentStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.*;

@DisplayName("Payment Entity Unit Tests")
class PaymentEntityTest {

    @Test
    @DisplayName("Should create payment entity")
    void testPaymentCreation() {
        Payment payment = Payment.builder()
            .id(1L)
            .orderId(123L)
            .amount(BigDecimal.valueOf(99.99))
            .status(PaymentStatus.PROCESSING)
            .build();

        assertThat(payment.getId()).isEqualTo(1L);
        assertThat(payment.getOrderId()).isEqualTo(123L);
        assertThat(payment.getAmount()).isEqualByComparingTo(BigDecimal.valueOf(99.99));
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PROCESSING);
    }

    @Test
    @DisplayName("Should update payment status")
    void testUpdatePaymentStatus() {
        Payment payment = Payment.builder()
            .status(PaymentStatus.PROCESSING)
            .build();

        payment.setStatus(PaymentStatus.PROCESSED);

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PROCESSED);
    }

    @Test
    @DisplayName("Should handle payment refund")
    void testPaymentRefund() {
        Payment payment = Payment.builder()
            .status(PaymentStatus.PROCESSED)
            .build();

        payment.setStatus(PaymentStatus.REFUNDED);

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.REFUNDED);
    }
}
