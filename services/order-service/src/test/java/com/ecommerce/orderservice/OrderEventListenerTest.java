package com.ecommerce.orderservice;

import com.ecommerce.common.events.InventoryReservedEvent;
import com.ecommerce.common.events.InventoryFailedEvent;
import com.ecommerce.common.events.OrderCancelledEvent;
import com.ecommerce.common.events.EventPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.support.Acknowledgment;

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

    private InventoryReservedEvent inventoryReservedEvent;
    private InventoryFailedEvent inventoryFailedEvent;
    private Order order;

    @BeforeEach
    void setUp() {
        String orderId = "order-123";
        String eventId = UUID.randomUUID().toString();

        inventoryReservedEvent = new InventoryReservedEvent(orderId, "PROD-001", 5);
        inventoryReservedEvent.setEventId(eventId);

        inventoryFailedEvent = new InventoryFailedEvent(orderId);
        inventoryFailedEvent.setEventId(eventId);

        order = Order.builder()
            .id(1L)
            .customerId("customer-123")
            .productId("PROD-001")
            .quantity(5)
            .status(OrderStatus.PENDING)
            .build();
    }

    @Test
    @DisplayName("Should update order status when inventory is reserved")
    void testHandleInventoryReservedSuccess() {
        when(repository.findByOrderId("order-123")).thenReturn(Optional.of(order));
        when(repository.save(any())).thenReturn(order);

        listener.handleInventoryReserved(inventoryReservedEvent, acknowledgment);

        verify(repository).findByOrderId("order-123");
        verify(repository).save(any(Order.class));
        verify(acknowledgment).acknowledge();
    }

    @Test
    @DisplayName("Should handle inventory reserved when order not found")
    void testHandleInventoryReservedOrderNotFound() {
        when(repository.findByOrderId("order-123")).thenReturn(Optional.empty());

        listener.handleInventoryReserved(inventoryReservedEvent, acknowledgment);

        verify(repository).findByOrderId("order-123");
        verify(repository, never()).save(any());
        verify(acknowledgment).acknowledge();
    }

    @Test
    @DisplayName("Should cancel order when inventory fails")
    void testHandleInventoryFailedSuccess() {
        when(repository.findByOrderId("order-123")).thenReturn(Optional.of(order));
        when(repository.save(any())).thenReturn(order);

        listener.handleInventoryFailed(inventoryFailedEvent, acknowledgment);

        verify(repository).findByOrderId("order-123");
        verify(repository).save(any(Order.class));
        verify(eventPublisher).publishEvent(any(OrderCancelledEvent.class), anyString(), anyString(), anyString());
        verify(acknowledgment).acknowledge();
    }

    @Test
    @DisplayName("Should handle inventory failed when order not found")
    void testHandleInventoryFailedOrderNotFound() {
        when(repository.findByOrderId("order-123")).thenReturn(Optional.empty());

        listener.handleInventoryFailed(inventoryFailedEvent, acknowledgment);

        verify(repository).findByOrderId("order-123");
        verify(repository, never()).save(any());
        verify(acknowledgment).acknowledge();
    }

    @Test
    @DisplayName("Should handle exception during inventory reserved processing")
    void testHandleInventoryReservedException() {
        when(repository.findByOrderId("order-123")).thenThrow(new RuntimeException("Database error"));

        listener.handleInventoryReserved(inventoryReservedEvent, acknowledgment);

        verify(acknowledgment).acknowledge();
    }

    @Test
    @DisplayName("Should handle exception during inventory failed processing")
    void testHandleInventoryFailedException() {
        when(repository.findByOrderId("order-123")).thenThrow(new RuntimeException("Database error"));

        listener.handleInventoryFailed(inventoryFailedEvent, acknowledgment);

        verify(acknowledgment).acknowledge();
    }
}
