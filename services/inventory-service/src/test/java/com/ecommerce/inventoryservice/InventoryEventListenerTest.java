package com.ecommerce.inventoryservice;

import com.ecommerce.common.events.InventoryFailedEvent;
import com.ecommerce.common.events.InventoryReservedEvent;
import com.ecommerce.common.events.InventoryReleasedEvent;
import com.ecommerce.common.events.OrderCreatedEvent;
import com.ecommerce.common.events.PaymentFailedEvent;
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
@DisplayName("InventoryEventListener Unit Tests")
class InventoryEventListenerTest {

    @Mock
    private InventoryRepository repository;

    @Mock
    private EventPublisher eventPublisher;

    @Mock
    private Acknowledgment acknowledgment;

    @InjectMocks
    private InventoryEventListener listener;

    private OrderCreatedEvent orderCreatedEvent;
    private PaymentFailedEvent paymentFailedEvent;
    private Inventory inventory;

    @BeforeEach
    void setUp() {
        String orderId = "order-123";
        String productId = "PROD-001";
        String eventId = UUID.randomUUID().toString();

        orderCreatedEvent = new OrderCreatedEvent(orderId, productId, 5, 99.99);
        orderCreatedEvent.setEventId(eventId);

        paymentFailedEvent = new PaymentFailedEvent(orderId);
        paymentFailedEvent.setEventId(eventId);
        paymentFailedEvent.setProductId(productId);
        paymentFailedEvent.setQuantity(5);

        inventory = Inventory.builder()
            .id(1L)
            .productId(productId)
            .quantity(100)
            .build();
    }

    @Test
    @DisplayName("Should reserve inventory when order is created")
    void testHandleOrderCreatedSuccess() {
        when(repository.findByProductId("PROD-001")).thenReturn(Optional.of(inventory));
        when(repository.save(any())).thenReturn(inventory);

        listener.handleOrderCreated(orderCreatedEvent, acknowledgment);

        verify(repository).findByProductId("PROD-001");
        verify(repository).save(any(Inventory.class));
        verify(eventPublisher).publishEvent(any(InventoryReservedEvent.class), anyString(), anyString(), anyString());
        verify(acknowledgment).acknowledge();
    }

    @Test
    @DisplayName("Should publish failure event when insufficient inventory")
    void testHandleOrderCreatedInsufficientStock() {
        inventory.setQuantity(2); // Less than requested 5
        when(repository.findByProductId("PROD-001")).thenReturn(Optional.of(inventory));

        listener.handleOrderCreated(orderCreatedEvent, acknowledgment);

        verify(repository).findByProductId("PROD-001");
        verify(repository, never()).save(any());
        verify(eventPublisher).publishEvent(any(InventoryFailedEvent.class), anyString(), anyString(), anyString());
        verify(acknowledgment).acknowledge();
    }

    @Test
    @DisplayName("Should publish failure event when product not found")
    void testHandleOrderCreatedProductNotFound() {
        when(repository.findByProductId("PROD-001")).thenReturn(Optional.empty());

        listener.handleOrderCreated(orderCreatedEvent, acknowledgment);

        verify(repository).findByProductId("PROD-001");
        verify(repository, never()).save(any());
        verify(eventPublisher).publishEvent(any(InventoryFailedEvent.class), anyString(), anyString(), anyString());
        verify(acknowledgment).acknowledge();
    }

    @Test
    @DisplayName("Should release inventory when payment fails")
    void testHandlePaymentFailedSuccess() {
        when(repository.findByProductId("PROD-001")).thenReturn(Optional.of(inventory));
        when(repository.save(any())).thenReturn(inventory);

        listener.handlePaymentFailed(paymentFailedEvent, acknowledgment);

        verify(repository).findByProductId("PROD-001");
        verify(repository).save(any(Inventory.class));
        verify(eventPublisher).publishEvent(any(InventoryReleasedEvent.class), anyString(), anyString(), anyString());
        verify(acknowledgment).acknowledge();
    }

    @Test
    @DisplayName("Should handle payment failed when inventory not found")
    void testHandlePaymentFailedInventoryNotFound() {
        when(repository.findByProductId("PROD-001")).thenReturn(Optional.empty());

        listener.handlePaymentFailed(paymentFailedEvent, acknowledgment);

        verify(repository).findByProductId("PROD-001");
        verify(repository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any(), anyString(), anyString(), anyString());
        verify(acknowledgment).acknowledge();
    }

    @Test
    @DisplayName("Should handle payment failed without product info")
    void testHandlePaymentFailedNoProductInfo() {
        PaymentFailedEvent event = new PaymentFailedEvent("order-456");
        event.setEventId(UUID.randomUUID().toString());

        listener.handlePaymentFailed(event, acknowledgment);

        verify(repository, never()).findByProductId(anyString());
        verify(acknowledgment).acknowledge();
    }

    @Test
    @DisplayName("Should handle exception during order created processing")
    void testHandleOrderCreatedException() {
        when(repository.findByProductId("PROD-001")).thenThrow(new RuntimeException("Database error"));

        listener.handleOrderCreated(orderCreatedEvent, acknowledgment);

        verify(acknowledgment).acknowledge();
    }

    @Test
    @DisplayName("Should handle exception during payment failed processing")
    void testHandlePaymentFailedException() {
        when(repository.findByProductId("PROD-001")).thenThrow(new RuntimeException("Database error"));

        listener.handlePaymentFailed(paymentFailedEvent, acknowledgment);

        verify(acknowledgment).acknowledge();
    }
}
