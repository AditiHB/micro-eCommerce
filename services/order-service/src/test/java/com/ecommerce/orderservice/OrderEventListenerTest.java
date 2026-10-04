package com.ecommerce.orderservice;

import com.ecommerce.common.enums.OrderStatus;
import com.ecommerce.common.events.DlqPublisher;
import com.ecommerce.common.events.InventoryFailedEvent;
import com.ecommerce.common.events.OrderCancelledEvent;
import com.ecommerce.common.events.PaymentFailedEvent;
import com.ecommerce.common.events.PaymentProcessedEvent;
import com.ecommerce.common.events.RefundCompletedEvent;
import com.ecommerce.common.events.EventPublisher;
import com.ecommerce.orderservice.dto.OrderResponse;
import com.ecommerce.orderservice.service.OrderService;
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
@DisplayName("OrderEventListener Unit Tests")
class OrderEventListenerTest {

    @Mock
    private OrderRepository repository;

    @Mock
    private OrderService orderService;

    @Mock
    private EventPublisher eventPublisher;

    @Mock
    private DlqPublisher dlqPublisher;

    @Mock
    private Acknowledgment acknowledgment;

    @InjectMocks
    private OrderEventListener listener;

    private PaymentProcessedEvent paymentProcessedEvent;
    private InventoryFailedEvent inventoryFailedEvent;
    private PaymentFailedEvent paymentFailedEvent;
    private RefundCompletedEvent refundCompletedEvent;
    private Order order;
    private OrderResponse orderResponse;

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

        orderResponse = OrderResponse.builder()
            .id(123L)
            .customerId(1L)
            .productId("PROD-001")
            .quantity(5)
            .status(OrderStatus.CANCELLED)
            .build();
    }

    @Test
    @DisplayName("Should update order status to COMPLETED when payment is processed")
    void testHandlePaymentProcessedSuccess() {
        when(orderService.updateOrderStatusIfPresent(123L, OrderStatus.COMPLETED))
            .thenReturn(Optional.of(orderResponse));

        listener.handlePaymentProcessed(paymentProcessedEvent, acknowledgment);

        verify(orderService).updateOrderStatusIfPresent(123L, OrderStatus.COMPLETED);
        verify(acknowledgment).acknowledge();
    }

    @Test
    @DisplayName("Should handle payment processed when order not found")
    void testHandlePaymentProcessedOrderNotFound() {
        when(orderService.updateOrderStatusIfPresent(123L, OrderStatus.COMPLETED))
            .thenReturn(Optional.empty());

        listener.handlePaymentProcessed(paymentProcessedEvent, acknowledgment);

        verify(orderService).updateOrderStatusIfPresent(123L, OrderStatus.COMPLETED);
        verify(acknowledgment).acknowledge();
    }

    @Test
    @DisplayName("Should cancel order when inventory fails")
    void testHandleInventoryFailedSuccess() {
        when(orderService.updateOrderStatusIfPresent(123L, OrderStatus.CANCELLED))
            .thenReturn(Optional.of(orderResponse));

        listener.handleInventoryFailed(inventoryFailedEvent, acknowledgment);

        verify(orderService).updateOrderStatusIfPresent(123L, OrderStatus.CANCELLED);
        verify(eventPublisher).publishEvent(any(OrderCancelledEvent.class), anyString(), anyString(), anyString());
        verify(acknowledgment).acknowledge();
    }

    @Test
    @DisplayName("Should handle inventory failed when order not found")
    void testHandleInventoryFailedOrderNotFound() {
        when(orderService.updateOrderStatusIfPresent(123L, OrderStatus.CANCELLED))
            .thenReturn(Optional.empty());

        listener.handleInventoryFailed(inventoryFailedEvent, acknowledgment);

        verify(orderService).updateOrderStatusIfPresent(123L, OrderStatus.CANCELLED);
        verify(eventPublisher, never()).publishEvent(any(OrderCancelledEvent.class), anyString(), anyString(), anyString());
        verify(acknowledgment).acknowledge();
    }

    @Test
    @DisplayName("Should cancel order when payment fails")
    void testHandlePaymentFailedSuccess() {
        when(orderService.updateOrderStatusIfPresent(123L, OrderStatus.CANCELLED))
            .thenReturn(Optional.of(orderResponse));

        listener.handlePaymentFailed(paymentFailedEvent, acknowledgment);

        verify(orderService).updateOrderStatusIfPresent(123L, OrderStatus.CANCELLED);
        verify(eventPublisher).publishEvent(any(OrderCancelledEvent.class), anyString(), anyString(), anyString());
        verify(acknowledgment).acknowledge();
    }

    @Test
    @DisplayName("Should handle payment failed when order not found")
    void testHandlePaymentFailedOrderNotFound() {
        when(orderService.updateOrderStatusIfPresent(123L, OrderStatus.CANCELLED))
            .thenReturn(Optional.empty());

        listener.handlePaymentFailed(paymentFailedEvent, acknowledgment);

        verify(orderService).updateOrderStatusIfPresent(123L, OrderStatus.CANCELLED);
        verify(eventPublisher, never()).publishEvent(any(OrderCancelledEvent.class), anyString(), anyString(), anyString());
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
    @DisplayName("Should route to DLQ and still ack when payment processed handling throws")
    void testHandlePaymentProcessedException() {
        when(orderService.updateOrderStatusIfPresent(123L, OrderStatus.COMPLETED))
            .thenThrow(new RuntimeException("Database error"));

        listener.handlePaymentProcessed(paymentProcessedEvent, acknowledgment);

        verify(dlqPublisher).publish(eq(paymentProcessedEvent), eq("payment-processed"), any(RuntimeException.class));
        verify(acknowledgment).acknowledge();
    }

    @Test
    @DisplayName("Should route to DLQ and still ack when inventory failed processing throws")
    void testHandleInventoryFailedException() {
        when(orderService.updateOrderStatusIfPresent(123L, OrderStatus.CANCELLED))
            .thenThrow(new RuntimeException("Database error"));

        listener.handleInventoryFailed(inventoryFailedEvent, acknowledgment);

        verify(dlqPublisher).publish(eq(inventoryFailedEvent), eq("inventory-failed"), any(RuntimeException.class));
        verify(acknowledgment).acknowledge();
    }

    @Test
    @DisplayName("Should route to DLQ and still ack when payment failed processing throws")
    void testHandlePaymentFailedException() {
        when(orderService.updateOrderStatusIfPresent(123L, OrderStatus.CANCELLED))
            .thenThrow(new RuntimeException("Database error"));

        listener.handlePaymentFailed(paymentFailedEvent, acknowledgment);

        verify(dlqPublisher).publish(eq(paymentFailedEvent), eq("payment-failed"), any(RuntimeException.class));
        verify(acknowledgment).acknowledge();
    }
}
