package com.ecommerce.orderservice;

import com.ecommerce.common.enums.OrderStatus;
import com.ecommerce.common.events.InventoryFailedEvent;
import com.ecommerce.common.events.OrderCancelledEvent;
import com.ecommerce.common.events.PaymentFailedEvent;
import com.ecommerce.common.events.PaymentProcessedEvent;
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
@DisplayName("OrderEventListener Unit Tests")
class OrderEventListenerTest {

    @Mock
    private OrderRepository repository;

    @Mock
    private EventPublisher eventPublisher;

    @Mock
    private Acknowledgment acknowledgment;

    @InjectMocks
    private OrderEventListener listener;

    private PaymentProcessedEvent paymentProcessedEvent;
    private InventoryFailedEvent inventoryFailedEvent;
    private PaymentFailedEvent paymentFailedEvent;
    private RefundCompletedEvent refundCompletedEvent;
    private Order order;

    @BeforeEach
    void setUp() {
        Long orderId = 123L;
        String eventId = UUID.randomUUID().toString();

        paymentProcessedEvent = new PaymentProcessedEvent(1L, orderId, BigDecimal.valueOf(99.99));
        paymentProcessedEvent.setEventId(eventId);

        inventoryFailedEvent = new InventoryFailedEvent(orderId);
        inventoryFailedEvent.setEventId(eventId);

        paymentFailedEvent = new PaymentFailedEvent(orderId);
        paymentFailedEvent.setEventId(eventId);

        refundCompletedEvent = new RefundCompletedEvent(orderId, 1L, BigDecimal.valueOf(99.99));
        refundCompletedEvent.setEventId(eventId);

        order = Order.builder()
            .id(123L)
            .customerId(1L)
            .productId("PROD-001")
            .quantity(5)
            .status(OrderStatus.PENDING)
            .build();
    }

    @Test
    @DisplayName("Should update order status to COMPLETED when payment is processed")
    void testHandlePaymentProcessedSuccess() {
        when(repository.findById(123L)).thenReturn(Optional.of(order));
        when(repository.save(any())).thenReturn(order);

        listener.handlePaymentProcessed(paymentProcessedEvent, acknowledgment);

        verify(repository).findById(123L);
        verify(repository).save(any(Order.class));
        verify(acknowledgment).acknowledge();
    }

    @Test
    @DisplayName("Should handle payment processed when order not found")
    void testHandlePaymentProcessedOrderNotFound() {
        when(repository.findById(123L)).thenReturn(Optional.empty());

        listener.handlePaymentProcessed(paymentProcessedEvent, acknowledgment);

        verify(repository).findById(123L);
        verify(repository, never()).save(any());
        verify(acknowledgment).acknowledge();
    }

    @Test
    @DisplayName("Should cancel order when inventory fails")
    void testHandleInventoryFailedSuccess() {
        when(repository.findById(123L)).thenReturn(Optional.of(order));
        when(repository.save(any())).thenReturn(order);

        listener.handleInventoryFailed(inventoryFailedEvent, acknowledgment);

        verify(repository).findById(123L);
        verify(repository).save(any(Order.class));
        verify(eventPublisher).publishEvent(any(OrderCancelledEvent.class), anyString(), anyString(), anyString());
        verify(acknowledgment).acknowledge();
    }

    @Test
    @DisplayName("Should handle inventory failed when order not found")
    void testHandleInventoryFailedOrderNotFound() {
        when(repository.findById(123L)).thenReturn(Optional.empty());

        listener.handleInventoryFailed(inventoryFailedEvent, acknowledgment);

        verify(repository).findById(123L);
        verify(repository, never()).save(any());
        verify(acknowledgment).acknowledge();
    }

    @Test
    @DisplayName("Should cancel order when payment fails")
    void testHandlePaymentFailedSuccess() {
        when(repository.findById(123L)).thenReturn(Optional.of(order));
        when(repository.save(any())).thenReturn(order);

        listener.handlePaymentFailed(paymentFailedEvent, acknowledgment);

        verify(repository).findById(123L);
        verify(repository).save(any(Order.class));
        verify(eventPublisher).publishEvent(any(OrderCancelledEvent.class), anyString(), anyString(), anyString());
        verify(acknowledgment).acknowledge();
    }

    @Test
    @DisplayName("Should handle payment failed when order not found")
    void testHandlePaymentFailedOrderNotFound() {
        when(repository.findById(123L)).thenReturn(Optional.empty());

        listener.handlePaymentFailed(paymentFailedEvent, acknowledgment);

        verify(repository).findById(123L);
        verify(repository, never()).save(any());
        verify(acknowledgment).acknowledge();
    }

    @Test
    @DisplayName("Should log completion when refund is completed")
    void testHandleRefundCompletedSuccess() {
        when(repository.findById(123L)).thenReturn(Optional.of(order));

        listener.handleRefundCompleted(refundCompletedEvent, acknowledgment);

        verify(repository).findById(123L);
        verify(acknowledgment).acknowledge();
    }

    @Test
    @DisplayName("Should handle exception during payment processed handling")
    void testHandlePaymentProcessedException() {
        when(repository.findById(123L)).thenThrow(new RuntimeException("Database error"));

        listener.handlePaymentProcessed(paymentProcessedEvent, acknowledgment);

        verify(acknowledgment, never()).acknowledge();
    }

    @Test
    @DisplayName("Should handle exception during inventory failed processing")
    void testHandleInventoryFailedException() {
        when(repository.findById(123L)).thenThrow(new RuntimeException("Database error"));

        listener.handleInventoryFailed(inventoryFailedEvent, acknowledgment);

        verify(acknowledgment, never()).acknowledge();
    }
}
