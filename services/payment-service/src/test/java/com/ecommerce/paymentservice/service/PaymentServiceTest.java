package com.ecommerce.paymentservice.service;

import com.ecommerce.common.enums.PaymentStatus;
import com.ecommerce.common.events.EventPublisher;
import com.ecommerce.common.events.PaymentFailedEvent;
import com.ecommerce.common.events.PaymentProcessedEvent;
import com.ecommerce.common.events.RefundCompletedEvent;
import com.ecommerce.common.exception.BusinessException;
import com.ecommerce.common.exception.ResourceNotFoundException;
import com.ecommerce.common.exception.UnprocessableEntityException;
import com.ecommerce.paymentservice.Payment;
import com.ecommerce.paymentservice.PaymentRepository;
import com.ecommerce.paymentservice.exception.InvalidPaymentTransitionException;
import com.ecommerce.paymentservice.gateway.PaymentGateway;
import com.ecommerce.paymentservice.gateway.PaymentGateway.Outcome;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Money-moving rules, with the processor and the database mocked; the same flows on a real database are in the integration tests. */
@ExtendWith(MockitoExtension.class)
@DisplayName("PaymentService")
class PaymentServiceTest {

    private static final BigDecimal AMOUNT = new BigDecimal("172.97");

    @Mock
    private PaymentRepository payments;
    @Mock
    private PaymentGateway gateway;
    @Mock
    private EventPublisher events;

    private PaymentService service;

    @BeforeEach
    void setUp() {
        service = new PaymentService(payments, gateway, events);
        org.mockito.Mockito.lenient().when(payments.saveAndFlush(any(Payment.class))).thenAnswer(inv -> {
            Payment p = inv.getArgument(0);
            if (p.getId() == null) {
                p.setId(9L);
            }
            return p;
        });
    }

    private Payment captured() {
        Payment payment = Payment.pending(42L, 7L, AMOUNT, "USD");
        payment.setId(9L);
        payment.authorized("auth-1");
        payment.captured("cap-1");
        return payment;
    }

    // ------------------------------------------------------------------ charging

    @Test
    @DisplayName("charges the order's total: authorize, capture, then announce payment.processed with the amount")
    void chargeSucceeds() {
        when(gateway.authorize("order-42", AMOUNT, "USD")).thenReturn(Outcome.approved("auth-1"));
        when(gateway.capture("order-42", "auth-1")).thenReturn(Outcome.approved("cap-1"));

        Payment payment = service.charge(42L, 7L, AMOUNT, "USD");

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.CAPTURED);
        assertThat(payment.getAmount()).isEqualByComparingTo("172.97");
        assertThat(payment.getProcessorReference()).isEqualTo("cap-1");
        org.mockito.ArgumentCaptor<PaymentProcessedEvent> event = org.mockito.ArgumentCaptor.forClass(PaymentProcessedEvent.class);
        verify(events).publish(event.capture());
        assertThat(event.getValue().getAmount()).isEqualByComparingTo("172.97");
        assertThat(event.getValue().getCurrency()).isEqualTo("USD");
        assertThat(event.getValue().getCustomerId()).isEqualTo(7L);
        assertThat(event.getValue().getOrderId()).isEqualTo(42L);
    }

    @Test
    @DisplayName("a decline is a normal outcome: the payment is FAILED and payment.failed carries the reason")
    void declineIsAnOutcome() {
        when(gateway.authorize(anyString(), any(), anyString())).thenReturn(Outcome.declined("Card declined"));

        Payment payment = service.charge(42L, 7L, AMOUNT, "USD");

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(payment.getFailureReason()).isEqualTo("Card declined");
        org.mockito.ArgumentCaptor<PaymentFailedEvent> event = org.mockito.ArgumentCaptor.forClass(PaymentFailedEvent.class);
        verify(events).publish(event.capture());
        assertThat(event.getValue().getReason()).isEqualTo("Card declined");
        verify(gateway, never()).capture(anyString(), anyString());
    }

    @Test
    @DisplayName("a capture that is refused after a successful authorization also ends FAILED, never half-charged")
    void captureDecline() {
        when(gateway.authorize(anyString(), any(), anyString())).thenReturn(Outcome.approved("auth-1"));
        when(gateway.capture(anyString(), anyString())).thenReturn(Outcome.declined("Authorization expired"));

        Payment payment = service.charge(42L, 7L, AMOUNT, "USD");

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
        verify(events).publish(any(PaymentFailedEvent.class));
    }

    @Test
    @DisplayName("a processor that cannot be reached is an exception (retried), not a decline: no payment.failed is sent")
    void processorOutage() {
        when(gateway.authorize(anyString(), any(), anyString())).thenThrow(new IllegalStateException("connect timed out"));

        assertThatThrownBy(() -> service.charge(42L, 7L, AMOUNT, "USD")).hasMessage("connect timed out");

        verify(events, never()).publish(any());
    }

    // ------------------------------------------------------------------ refunds

    @Test
    @DisplayName("refunding a captured payment goes through the processor and announces refund.completed")
    void refund() {
        Payment payment = captured();
        when(payments.findById(9L)).thenReturn(Optional.of(payment));
        when(gateway.refund(eq("refund-9"), eq("cap-1"), eq(AMOUNT))).thenReturn(Outcome.approved("ref-1"));

        assertThat(service.refundPayment(9L).getStatus()).isEqualTo(PaymentStatus.REFUNDED);

        org.mockito.ArgumentCaptor<RefundCompletedEvent> event = org.mockito.ArgumentCaptor.forClass(RefundCompletedEvent.class);
        verify(events).publish(event.capture());
        assertThat(event.getValue().getRefundAmount()).isEqualByComparingTo("172.97");
        assertThat(event.getValue().getPaymentId()).isEqualTo(9L);
    }

    @Test
    @DisplayName("a payment that was never captured is refused BEFORE the processor is called")
    void refundUncapturedIsRefusedEarly() {
        Payment pending = Payment.pending(42L, 7L, AMOUNT, "USD");
        pending.setId(9L);
        when(payments.findById(9L)).thenReturn(Optional.of(pending));

        assertThatThrownBy(() -> service.refundPayment(9L)).isInstanceOf(InvalidPaymentTransitionException.class);

        verify(gateway, never()).refund(anyString(), any(), any());
        verify(events, never()).publish(any());
    }

    @Test
    @DisplayName("a second refund is refused (409) without calling the processor again")
    void secondRefund() {
        Payment payment = captured();
        payment.refunded("ref-1");
        when(payments.findById(9L)).thenReturn(Optional.of(payment));

        assertThatThrownBy(() -> service.refundPayment(9L)).isInstanceOf(InvalidPaymentTransitionException.class);

        verify(gateway, never()).refund(anyString(), any(), any());
    }

    @Test
    @DisplayName("a refund the processor refuses is a 422 and the payment stays CAPTURED")
    void refundDeclined() {
        Payment payment = captured();
        when(payments.findById(9L)).thenReturn(Optional.of(payment));
        when(gateway.refund(anyString(), any(), any())).thenReturn(Outcome.declined("Dispute open"));

        assertThatThrownBy(() -> service.refundPayment(9L)).isInstanceOf(UnprocessableEntityException.class);

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.CAPTURED);
        verify(events, never()).publish(any());
    }

    // ------------------------------------------------------------------ reading

    @Test
    @DisplayName("an unknown payment, or an order with no payment, is a 404")
    void notFound() {
        when(payments.findById(1L)).thenReturn(Optional.empty());
        when(payments.findByOrderId(5L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getPayment(1L)).isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> service.getPaymentByOrder(5L)).isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> service.refundPayment(1L)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("sorting is limited to known fields")
    void sortAllowList() {
        assertThatThrownBy(() -> service.getAllPayments(0, 20, "processorReference"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode()).isEqualTo("INVALID_SORT_FIELD");
    }
}
