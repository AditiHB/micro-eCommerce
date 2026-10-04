package com.ecommerce.inventoryservice;

import com.ecommerce.common.events.DlqPublisher;
import com.ecommerce.common.events.InventoryFailedEvent;
import com.ecommerce.common.events.InventoryReservedEvent;
import com.ecommerce.common.events.InventoryReleasedEvent;
import com.ecommerce.common.events.OrderCreatedEvent;
import com.ecommerce.common.events.PaymentFailedEvent;
import com.ecommerce.common.events.EventPublisher;
import com.ecommerce.inventoryservice.dto.InventoryResponse;
import com.ecommerce.inventoryservice.service.InventoryService;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("InventoryEventListener Unit Tests")
class InventoryEventListenerTest {

    @Mock
    private InventoryService inventoryService;

    @Mock
    private EventPublisher eventPublisher;

    @Mock
    private DlqPublisher dlqPublisher;

    @Mock
    private Acknowledgment acknowledgment;

    @InjectMocks
    private InventoryEventListener listener;

    private OrderCreatedEvent orderCreatedEvent;
    private PaymentFailedEvent paymentFailedEvent;
    private InventoryResponse inventoryResponse;

    @BeforeEach
    void setUp() {
        Long orderId = 123L;
        Long customerId = 10L;
        String productId = "PROD-001";
        String eventId = UUID.randomUUID().toString();

        orderCreatedEvent = new OrderCreatedEvent(orderId, customerId, productId, 5);
        orderCreatedEvent.setEventId(eventId);

        paymentFailedEvent = new PaymentFailedEvent(orderId);
        paymentFailedEvent.setEventId(eventId);
        paymentFailedEvent.setProductId(productId);
        paymentFailedEvent.setQuantity(5);

        inventoryResponse = InventoryResponse.builder()
            .id(1L)
            .productId(productId)
            .quantity(95)
            .build();
    }

    @Test
    @DisplayName("Should reserve inventory when order is created")
    void testHandleOrderCreatedSuccess() {
        when(inventoryService.reserveStockIfAvailable(123L, "PROD-001", 5)).thenReturn(Optional.of(inventoryResponse));

        listener.handleOrderCreated(orderCreatedEvent, acknowledgment);

        verify(inventoryService).reserveStockIfAvailable(123L, "PROD-001", 5);
        verify(eventPublisher).publishEvent(any(InventoryReservedEvent.class), anyString(), anyString(), anyString());
        verify(acknowledgment).acknowledge();
    }

    @Test
    @DisplayName("Should publish failure event when insufficient inventory")
    void testHandleOrderCreatedInsufficientStock() {
        when(inventoryService.reserveStockIfAvailable(123L, "PROD-001", 5)).thenReturn(Optional.empty());

        listener.handleOrderCreated(orderCreatedEvent, acknowledgment);

        verify(inventoryService).reserveStockIfAvailable(123L, "PROD-001", 5);
        verify(eventPublisher).publishEvent(any(InventoryFailedEvent.class), anyString(), anyString(), anyString());
        verify(acknowledgment).acknowledge();
    }

    @Test
    @DisplayName("Should publish failure event when product not found")
    void testHandleOrderCreatedProductNotFound() {
        when(inventoryService.reserveStockIfAvailable(123L, "PROD-001", 5)).thenReturn(Optional.empty());

        listener.handleOrderCreated(orderCreatedEvent, acknowledgment);

        verify(inventoryService).reserveStockIfAvailable(123L, "PROD-001", 5);
        verify(eventPublisher).publishEvent(any(InventoryFailedEvent.class), anyString(), anyString(), anyString());
        verify(acknowledgment).acknowledge();
    }

    @Test
    @DisplayName("Should release inventory when payment fails")
    void testHandlePaymentFailedSuccess() {
        when(inventoryService.releaseStockIfPresent(123L, "PROD-001", 5)).thenReturn(Optional.of(inventoryResponse));

        listener.handlePaymentFailed(paymentFailedEvent, acknowledgment);

        verify(inventoryService).releaseStockIfPresent(123L, "PROD-001", 5);
        verify(eventPublisher).publishEvent(any(InventoryReleasedEvent.class), anyString(), anyString(), anyString());
        verify(acknowledgment).acknowledge();
    }

    @Test
    @DisplayName("Should handle payment failed when inventory not found")
    void testHandlePaymentFailedInventoryNotFound() {
        when(inventoryService.releaseStockIfPresent(123L, "PROD-001", 5)).thenReturn(Optional.empty());

        listener.handlePaymentFailed(paymentFailedEvent, acknowledgment);

        verify(inventoryService).releaseStockIfPresent(123L, "PROD-001", 5);
        verify(eventPublisher, never()).publishEvent(any(), anyString(), anyString(), anyString());
        verify(acknowledgment).acknowledge();
    }

    @Test
    @DisplayName("Should handle payment failed without product info")
    void testHandlePaymentFailedNoProductInfo() {
        PaymentFailedEvent event = new PaymentFailedEvent(456L);
        event.setEventId(UUID.randomUUID().toString());

        listener.handlePaymentFailed(event, acknowledgment);

        verify(inventoryService, never()).releaseStockIfPresent(any(), anyString(), any());
        verify(acknowledgment).acknowledge();
    }

    @Test
    @DisplayName("Should route to DLQ and still ack when order created processing throws")
    void testHandleOrderCreatedException() {
        when(inventoryService.reserveStockIfAvailable(123L, "PROD-001", 5)).thenThrow(new RuntimeException("Database error"));

        listener.handleOrderCreated(orderCreatedEvent, acknowledgment);

        verify(dlqPublisher).publish(eq(orderCreatedEvent), eq("order-created"), any(RuntimeException.class));
        verify(acknowledgment).acknowledge();
    }

    @Test
    @DisplayName("Should route to DLQ and still ack when payment failed processing throws")
    void testHandlePaymentFailedException() {
        when(inventoryService.releaseStockIfPresent(123L, "PROD-001", 5)).thenThrow(new RuntimeException("Database error"));

        listener.handlePaymentFailed(paymentFailedEvent, acknowledgment);

        verify(dlqPublisher).publish(eq(paymentFailedEvent), eq("payment-failed"), any(RuntimeException.class));
        verify(acknowledgment).acknowledge();
    }
}
