package com.ecommerce.notificationservice;

import com.ecommerce.common.events.OrderCreatedEvent;
import com.ecommerce.common.events.PaymentFailedEvent;
import com.ecommerce.common.events.PaymentProcessedEvent;
import com.ecommerce.notificationservice.client.OrderClient;
import com.ecommerce.notificationservice.client.OrderInfo;
import com.ecommerce.notificationservice.service.NotificationService;
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

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("NotificationEventListener Unit Tests")
class NotificationEventListenerTest {

    @Mock
    private NotificationService notificationService;

    @Mock
    private OrderClient orderClient;

    @Mock
    private Acknowledgment acknowledgment;

    @InjectMocks
    private NotificationEventListener listener;

    @Test
    @DisplayName("Should notify on order created and acknowledge")
    void testHandleOrderCreated() {
        OrderCreatedEvent event = new OrderCreatedEvent(100L, 10L, "PROD-001", 2);

        listener.handleOrderCreated(event, acknowledgment);

        verify(notificationService).notifyOrderCreated(event.getEventId(), 100L, 10L, "PROD-001", 2);
        verify(acknowledgment).acknowledge();
    }

    @Test
    @DisplayName("Should resolve customer via Order Service and notify on payment processed")
    void testHandlePaymentProcessed() {
        PaymentProcessedEvent event = new PaymentProcessedEvent(1L, 100L, new BigDecimal("49.99"));
        when(orderClient.getOrder(100L)).thenReturn(Optional.of(new OrderInfo(100L, 10L)));

        listener.handlePaymentProcessed(event, acknowledgment);

        verify(notificationService).notifyPaymentSuccess(event.getEventId(), 100L, 10L, new BigDecimal("49.99"));
        verify(acknowledgment).acknowledge();
    }

    @Test
    @DisplayName("Should skip notifying when the order can't be resolved")
    void testHandlePaymentProcessedOrderNotFound() {
        PaymentProcessedEvent event = new PaymentProcessedEvent(1L, 100L, new BigDecimal("49.99"));
        when(orderClient.getOrder(100L)).thenReturn(Optional.empty());

        listener.handlePaymentProcessed(event, acknowledgment);

        verify(notificationService, never()).notifyPaymentSuccess(any(), any(), any(), any());
        verify(acknowledgment).acknowledge();
    }

    @Test
    @DisplayName("Should resolve customer via Order Service and notify on payment failed")
    void testHandlePaymentFailed() {
        PaymentFailedEvent event = new PaymentFailedEvent(100L, "PROD-001", 2, "Card declined");
        when(orderClient.getOrder(100L)).thenReturn(Optional.of(new OrderInfo(100L, 10L)));

        listener.handlePaymentFailed(event, acknowledgment);

        verify(notificationService).notifyPaymentFailed(event.getEventId(), 100L, 10L, "Card declined");
        verify(acknowledgment).acknowledge();
    }

    @Test
    @DisplayName("Should acknowledge even when notification handling throws")
    void testHandleOrderCreatedException() {
        OrderCreatedEvent event = new OrderCreatedEvent(100L, 10L, "PROD-001", 2);
        doThrow(new RuntimeException("boom"))
            .when(notificationService).notifyOrderCreated(anyString(), anyLong(), anyLong(), anyString(), anyInt());

        listener.handleOrderCreated(event, acknowledgment);

        verify(acknowledgment).acknowledge();
    }
}
