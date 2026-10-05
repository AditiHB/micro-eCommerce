package com.ecommerce.paymentservice;

import com.ecommerce.common.enums.PaymentStatus;
import com.ecommerce.paymentservice.exception.InvalidPaymentTransitionException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Payment aggregate")
class PaymentEntityTest {

    private Payment payment() {
        Payment payment = Payment.pending(42L, 7L, new BigDecimal("172.97"), "USD");
        payment.setId(1L);
        return payment;
    }

    @Test
    @DisplayName("a new payment is PENDING and carries the order, customer, amount and currency")
    void pending() {
        Payment payment = payment();

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(payment.getOrderId()).isEqualTo(42L);
        assertThat(payment.getAmount()).isEqualByComparingTo("172.97");
        assertThat(payment.getCurrency()).isEqualTo("USD");
    }

    @Test
    @DisplayName("money moves PENDING -> AUTHORIZED -> CAPTURED -> REFUNDED, remembering the processor's references")
    void fullLifecycle() {
        Payment payment = payment();

        payment.authorized("auth-1");
        payment.captured("cap-1");
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.CAPTURED);
        assertThat(payment.getProcessorReference()).isEqualTo("cap-1");
        payment.refunded("ref-1");

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.REFUNDED);
        assertThat(payment.getProcessorReference()).isEqualTo("ref-1");
    }

    @Test
    @DisplayName("money that was never captured cannot be refunded")
    void cannotRefundUncaptured() {
        Payment payment = payment();

        assertThatThrownBy(payment::requireRefundable).isInstanceOf(InvalidPaymentTransitionException.class);
        assertThatThrownBy(() -> payment.refunded("x")).isInstanceOf(InvalidPaymentTransitionException.class);
        payment.authorized("auth-1");
        assertThatThrownBy(payment::requireRefundable).isInstanceOf(InvalidPaymentTransitionException.class);
    }

    @Test
    @DisplayName("a payment cannot be refunded twice")
    void cannotRefundTwice() {
        Payment payment = payment();
        payment.authorized("a");
        payment.captured("c");
        payment.refunded("r");

        assertThatThrownBy(payment::requireRefundable)
                .isInstanceOf(InvalidPaymentTransitionException.class).hasMessageContaining("final");
    }

    @Test
    @DisplayName("a declined payment is final and records why")
    void declined() {
        Payment payment = payment();

        payment.failed("Card declined");

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(payment.getFailureReason()).isEqualTo("Card declined");
        assertThatThrownBy(() -> payment.captured("c")).isInstanceOf(InvalidPaymentTransitionException.class);
    }

    @Test
    @DisplayName("there is no status setter to bypass the state machine")
    void noStatusSetter() {
        assertThat(Payment.class.getMethods()).noneMatch(m -> m.getName().equals("setStatus"));
    }
}
