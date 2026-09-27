package com.ecommerce.paymentservice;

import com.ecommerce.common.events.PaymentProcessedEvent;
import com.ecommerce.common.events.PaymentFailedEvent;
import com.ecommerce.common.events.RefundInitiatedEvent;
import com.ecommerce.common.events.RefundCompletedEvent;
import com.ecommerce.common.events.EventPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.support.Acknowledgment;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("PaymentEventListener Unit Tests")
class PaymentEventListenerTest {

    @Mock
    private PaymentRepository repository;

    @Mock
    private EventPublisher eventPublisher;

    @Mock
    private Acknowledgment acknowledgment;

    @InjectMocks
    private PaymentEventListener listener;

    private RefundInitiatedEvent refundInitiatedEvent;
    private Payment payment;

    @BeforeEach
    void setUp() {
        String orderId = "order-123";
        String eventId = UUID.randomUUID().toString();

        refundInitiatedEvent = new RefundInitiatedEvent(orderId);
        refundInitiatedEvent.setEventId(eventId);

        payment = Payment.builder()
            .id(1L)
            .orderId(orderId)
            .amount(BigDecimal.valueOf(99.99))
            .status(PaymentStatus.PROCESSED)
            .build();
    }

    @Test
    @DisplayName("Should handle refund initiation successfully")
    void testHandleRefundInitiatedSuccess() {
        when(repository.findByOrderId("order-123")).thenReturn(Optional.of(payment));
        when(repository.save(any())).thenReturn(payment);

        listener.handleRefundInitiated(refundInitiatedEvent, acknowledgment);

        verify(repository).findByOrderId("order-123");
        verify(repository).save(any(Payment.class));
        verify(eventPublisher).publishEvent(any(RefundCompletedEvent.class), anyString(), anyString(), anyString());
        verify(acknowledgment).acknowledge();
    }

    @Test
    @DisplayName("Should handle refund when payment not found")
    void testHandleRefundInitiatedPaymentNotFound() {
        when(repository.findByOrderId("order-123")).thenReturn(Optional.empty());

        listener.handleRefundInitiated(refundInitiatedEvent, acknowledgment);

        verify(repository).findByOrderId("order-123");
        verify(repository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any(), anyString(), anyString(), anyString());
        verify(acknowledgment).acknowledge();
    }

    @Test
    @DisplayName("Should handle exception during refund processing")
    void testHandleRefundInitiatedException() {
        when(repository.findByOrderId("order-123")).thenThrow(new RuntimeException("Database error"));

        listener.handleRefundInitiated(refundInitiatedEvent, acknowledgment);

        verify(acknowledgment).acknowledge();
    }

    @Test
    @DisplayName("Should process partial refund correctly")
    void testHandleRefundInitiatedPartialRefund() {
        payment.setAmount(BigDecimal.valueOf(50.00));
        when(repository.findByOrderId("order-123")).thenReturn(Optional.of(payment));
        when(repository.save(any())).thenReturn(payment);

        listener.handleRefundInitiated(refundInitiatedEvent, acknowledgment);

        verify(repository).save(any(Payment.class));
        verify(acknowledgment).acknowledge();
    }
}
