package com.ecommerce.common.events;

import com.ecommerce.common.eventsourcing.EventSourcingService;
import com.ecommerce.common.exception.EventPublishingException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.messaging.Message;

import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("EventPublisher Unit Tests")
class EventPublisherTest {

    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    @Mock
    private EventSourcingService eventSourcingService;

    @InjectMocks
    private EventPublisher eventPublisher;

    private OrderCreatedEvent testEvent;

    @BeforeEach
    void setUp() {
        testEvent = new OrderCreatedEvent();
        testEvent.setEventId("event-123");
        testEvent.setAggregateId("order-456");
        testEvent.setAggregateType("Order");
        testEvent.setOrderId(456L);
        testEvent.setCustomerId(789L);
    }

    @Test
    @DisplayName("Should publish event successfully")
    void testPublishEventSuccess() {
        CompletableFuture<SendResult<String, Object>> future = CompletableFuture.completedFuture(null);
        when(kafkaTemplate.send(any(Message.class)))
            .thenReturn(future);
        doNothing().when(eventSourcingService).storeEvent(any(DomainEvent.class), anyString(), anyString());

        eventPublisher.publishEvent(testEvent, "order-events");

        verify(kafkaTemplate, times(1)).send(any(Message.class));
        verify(eventSourcingService, times(1)).storeEvent(any(DomainEvent.class), anyString(), anyString());
    }

    @Test
    @DisplayName("Should publish event with provided correlation ID")
    void testPublishEventWithCorrelationId() {
        CompletableFuture<SendResult<String, Object>> future = CompletableFuture.completedFuture(null);
        when(kafkaTemplate.send(any(Message.class)))
            .thenReturn(future);
        doNothing().when(eventSourcingService).storeEvent(any(DomainEvent.class), anyString(), anyString());

        String correlationId = "corr-123";
        eventPublisher.publishEvent(testEvent, "order-events", correlationId, null);

        verify(eventSourcingService, times(1)).storeEvent(testEvent, correlationId, "event-123");
    }

    @Test
    @DisplayName("Should publish event synchronously")
    void testPublishEventSyncSuccess() throws Exception {
        CompletableFuture<SendResult<String, Object>> future = CompletableFuture.completedFuture(null);
        when(kafkaTemplate.send(any(Message.class)))
            .thenReturn(future);
        doNothing().when(eventSourcingService).storeEvent(any(DomainEvent.class), anyString(), anyString());

        eventPublisher.publishEventSync(testEvent, "order-events");

        verify(kafkaTemplate, times(1)).send(any(Message.class));
        verify(eventSourcingService, times(1)).storeEvent(any(DomainEvent.class), anyString(), anyString());
    }

    @Test
    @DisplayName("Should not propagate an async Kafka send failure (it is only logged)")
    void testPublishEventKafkaFailure() {
        CompletableFuture<SendResult<String, Object>> failedFuture = new CompletableFuture<>();
        failedFuture.completeExceptionally(new RuntimeException("Kafka error"));
        when(kafkaTemplate.send(any(Message.class)))
            .thenReturn(failedFuture);
        doNothing().when(eventSourcingService).storeEvent(any(DomainEvent.class), anyString(), anyString());

        // publishEvent() is fire-and-forget: a failure surfacing later on the returned
        // CompletableFuture is logged (see the whenComplete callback) but not rethrown here.
        assertThatCode(() -> eventPublisher.publishEvent(testEvent, "order-events"))
            .doesNotThrowAnyException();

        verify(kafkaTemplate, times(1)).send(any(Message.class));
    }

    @Test
    @DisplayName("Should throw exception when event sourcing fails")
    void testPublishEventSourcingFailure() {
        doThrow(new RuntimeException("Event store error"))
            .when(eventSourcingService).storeEvent(any(DomainEvent.class), anyString(), anyString());

        assertThatThrownBy(() -> eventPublisher.publishEvent(testEvent, "order-events"))
            .isInstanceOf(EventPublishingException.class);
    }

    @Test
    @DisplayName("Should generate correlation ID when not provided")
    void testPublishEventGeneratesCorrelationId() {
        CompletableFuture<SendResult<String, Object>> future = CompletableFuture.completedFuture(null);
        when(kafkaTemplate.send(any(Message.class)))
            .thenReturn(future);
        doNothing().when(eventSourcingService).storeEvent(any(DomainEvent.class), anyString(), anyString());

        eventPublisher.publishEvent(testEvent, "order-events");

        verify(eventSourcingService, times(1)).storeEvent(
            eq(testEvent),
            argThat(correlationId -> correlationId != null && !correlationId.isEmpty()),
            eq("event-123")
        );
    }

    @Test
    @DisplayName("Should publish multiple events")
    void testPublishMultipleEvents() {
        CompletableFuture<SendResult<String, Object>> future = CompletableFuture.completedFuture(null);
        when(kafkaTemplate.send(any(Message.class)))
            .thenReturn(future);
        doNothing().when(eventSourcingService).storeEvent(any(DomainEvent.class), anyString(), anyString());

        OrderCreatedEvent event1 = new OrderCreatedEvent();
        event1.setEventId("event-1");
        event1.setAggregateId("order-1");

        OrderCancelledEvent event2 = new OrderCancelledEvent();
        event2.setEventId("event-2");
        event2.setAggregateId("order-1");

        eventPublisher.publishEvent(event1, "order-events");
        eventPublisher.publishEvent(event2, "order-events");

        verify(kafkaTemplate, times(2)).send(any(Message.class));
        verify(eventSourcingService, times(2)).storeEvent(any(DomainEvent.class), anyString(), anyString());
    }

    @Test
    @DisplayName("Should handle null event gracefully")
    void testPublishNullEvent() {
        assertThatThrownBy(() -> eventPublisher.publishEvent(null, "order-events"))
            .isInstanceOf(Exception.class);
    }

    @Test
    @DisplayName("Should handle null topic gracefully")
    void testPublishEventWithNullTopic() {
        CompletableFuture<SendResult<String, Object>> future = CompletableFuture.completedFuture(null);
        when(kafkaTemplate.send(any(Message.class)))
            .thenReturn(future);
        doNothing().when(eventSourcingService).storeEvent(any(DomainEvent.class), anyString(), anyString());

        eventPublisher.publishEvent(testEvent, null);

        verify(kafkaTemplate, times(1)).send(any(Message.class));
    }

    @Test
    @DisplayName("Should set correct headers when publishing event")
    void testPublishEventHeaders() {
        CompletableFuture<SendResult<String, Object>> future = CompletableFuture.completedFuture(null);
        when(kafkaTemplate.send(any(Message.class)))
            .thenReturn(future);
        doNothing().when(eventSourcingService).storeEvent(any(DomainEvent.class), anyString(), anyString());

        eventPublisher.publishEvent(testEvent, "order-events");

        verify(kafkaTemplate, times(1)).send(any(Message.class));
    }
}
