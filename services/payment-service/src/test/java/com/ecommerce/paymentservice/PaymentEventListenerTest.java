package com.ecommerce.paymentservice;

import com.ecommerce.common.enums.PaymentStatus;
import com.ecommerce.common.events.InventoryReservedEvent;
import com.ecommerce.common.events.OrderCancelledEvent;
import com.ecommerce.common.events.PaymentProcessedEvent;
import com.ecommerce.common.events.PaymentFailedEvent;
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
import static org.mockito.ArgumentMatchers.eq;
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

    private InventoryReservedEvent inventoryReservedEvent;
    private OrderCancelledEvent orderCancelledEvent;
    private Payment payment;

    @BeforeEach
    void setUp() {
        Long orderId = 123L;
        String eventId = UUID.randomUUID().toString();

        inventoryReservedEvent = new InventoryReservedEvent(orderId, "PROD-001", 5);
        inventoryReservedEvent.setEventId(eventId);

        orderCancelledEvent = new OrderCancelledEvent(orderId, "Customer requested cancellation");
        orderCancelledEvent.setEventId(eventId);

        payment = Payment.builder()
            .id(1L)
            .orderId(orderId)
            .amount(BigDecimal.valueOf(99.99))
            .status(PaymentStatus.PROCESSED)
            .build();
    }

    @Test
    @DisplayName("Should process payment when inventory is reserved")
    void testHandleInventoryReservedSuccess() {
        when(repository.save(any(Payment.class))).thenReturn(payment);

        listener.handleInventoryReserved(inventoryReservedEvent, acknowledgment);

        verify(repository).save(any(Payment.class));
        verify(eventPublisher).publishEvent(any(PaymentProcessedEvent.class), eq("payment-processed"), anyString(), anyString());
        verify(acknowledgment).acknowledge();
    }

    @Test
    @DisplayName("Should publish payment failed event and not ack when processing throws")
    void testHandleInventoryReservedException() {
        when(repository.save(any(Payment.class))).thenThrow(new RuntimeException("Database error"));

        listener.handleInventoryReserved(inventoryReservedEvent, acknowledgment);

        verify(eventPublisher).publishEvent(any(PaymentFailedEvent.class), eq("payment-failed"), anyString(), anyString());
        verify(acknowledgment, never()).acknowledge();
    }

    @Test
    @DisplayName("Should refund payment when order is cancelled")
    void testHandleOrderCancelledSuccess() {
        when(repository.findByOrderId(123L)).thenReturn(Optional.of(payment));
        when(repository.save(any(Payment.class))).thenReturn(payment);

        listener.handleOrderCancelled(orderCancelledEvent, acknowledgment);

        verify(repository).findByOrderId(123L);
        verify(repository).save(any(Payment.class));
        verify(eventPublisher).publishEvent(any(RefundCompletedEvent.class), eq("refund-completed"), anyString(), anyString());
        verify(acknowledgment).acknowledge();
    }

    @Test
    @DisplayName("Should handle order cancelled when payment not found")
    void testHandleOrderCancelledPaymentNotFound() {
        when(repository.findByOrderId(123L)).thenReturn(Optional.empty());

        listener.handleOrderCancelled(orderCancelledEvent, acknowledgment);

        verify(repository).findByOrderId(123L);
        verify(repository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any(), anyString(), anyString(), anyString());
        verify(acknowledgment).acknowledge();
    }

    @Test
    @DisplayName("Should not refund when payment is not in PROCESSED state")
    void testHandleOrderCancelledPaymentNotProcessed() {
        payment.setStatus(PaymentStatus.PROCESSING);
        when(repository.findByOrderId(123L)).thenReturn(Optional.of(payment));

        listener.handleOrderCancelled(orderCancelledEvent, acknowledgment);

        verify(repository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any(), anyString(), anyString(), anyString());
        verify(acknowledgment).acknowledge();
    }

    @Test
    @DisplayName("Should handle exception during order cancelled processing")
    void testHandleOrderCancelledException() {
        when(repository.findByOrderId(123L)).thenThrow(new RuntimeException("Database error"));

        listener.handleOrderCancelled(orderCancelledEvent, acknowledgment);

        verify(acknowledgment, never()).acknowledge();
    }
}
