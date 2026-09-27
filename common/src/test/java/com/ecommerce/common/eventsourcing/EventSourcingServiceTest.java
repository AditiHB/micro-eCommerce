package com.ecommerce.common.eventsourcing;

import com.ecommerce.common.events.DomainEvent;
import com.ecommerce.common.events.OrderCreatedEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("EventSourcingService Unit Tests")
class EventSourcingServiceTest {

    @Mock
    private EventStoreRepository eventStoreRepository;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private EventSourcingService eventSourcingService;

    private OrderCreatedEvent testEvent;
    private EventStore eventStore;

    @BeforeEach
    void setUp() {
        testEvent = new OrderCreatedEvent();
        testEvent.setEventId("event-123");
        testEvent.setAggregateId("order-456");
        testEvent.setAggregateType("Order");
        testEvent.setVersion(1);
        testEvent.setOccurredAt(LocalDateTime.now());

        eventStore = EventStore.builder()
            .id(1L)
            .eventId("event-123")
            .eventType("OrderCreated")
            .aggregateId("order-456")
            .aggregateType("Order")
            .eventData("{}")
            .version(1)
            .occurredAt(LocalDateTime.now())
            .build();
    }

    @Test
    @DisplayName("Should store event successfully")
    void testStoreEventSuccess() throws Exception {
        when(objectMapper.writeValueAsString(testEvent)).thenReturn("{}");
        when(eventStoreRepository.save(any(EventStore.class))).thenReturn(eventStore);

        eventSourcingService.storeEvent(testEvent);

        verify(eventStoreRepository, times(1)).save(any(EventStore.class));
    }

    @Test
    @DisplayName("Should store event with correlation ID")
    void testStoreEventWithCorrelationId() throws Exception {
        when(objectMapper.writeValueAsString(testEvent)).thenReturn("{}");
        when(eventStoreRepository.save(any(EventStore.class))).thenReturn(eventStore);

        eventSourcingService.storeEvent(testEvent, "corr-123", "caus-456");

        verify(eventStoreRepository, times(1)).save(argThat(es ->
            es.getCorrelationId().equals("corr-123") &&
            es.getCausationId().equals("caus-456")
        ));
    }

    @Test
    @DisplayName("Should throw exception when serialization fails")
    void testStoreEventSerializationFailure() throws Exception {
        when(objectMapper.writeValueAsString(testEvent)).thenThrow(new RuntimeException("Serialization error"));

        assertThatThrownBy(() -> eventSourcingService.storeEvent(testEvent))
            .isInstanceOf(EventSourcingException.class);
    }

    @Test
    @DisplayName("Should get events by aggregate ID")
    void testGetEventsByAggregateId() throws Exception {
        List<EventStore> events = Arrays.asList(eventStore);
        when(eventStoreRepository.findByAggregateId("order-456")).thenReturn(events);
        when(objectMapper.readValue("{}", OrderCreatedEvent.class)).thenReturn(testEvent);

        List<OrderCreatedEvent> result = eventSourcingService.getEventsByAggregateId("order-456", OrderCreatedEvent.class);

        assertThat(result).isNotEmpty();
        assertThat(result.get(0)).isNotNull();
    }

    @Test
    @DisplayName("Should get event history by aggregate ID")
    void testGetEventHistoryByAggregateId() {
        List<EventStore> events = Arrays.asList(eventStore);
        when(eventStoreRepository.findByAggregateId("order-456")).thenReturn(events);

        List<EventStore> result = eventSourcingService.getEventHistoryByAggregateId("order-456");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getAggregateId()).isEqualTo("order-456");
    }

    @Test
    @DisplayName("Should get events since version")
    void testGetEventsSince() {
        List<EventStore> events = Arrays.asList(eventStore);
        when(eventStoreRepository.findEventsSince("order-456", 1)).thenReturn(events);

        List<EventStore> result = eventSourcingService.getEventsBySince("order-456", 1);

        assertThat(result).isNotEmpty();
    }

    @Test
    @DisplayName("Should get latest event for aggregate")
    void testGetLatestEvent() {
        when(eventStoreRepository.findLatestEventForAggregate("order-456", "Order"))
            .thenReturn(Optional.of(eventStore));

        Optional<EventStore> result = eventSourcingService.getLatestEvent("order-456", "Order");

        assertThat(result).isPresent();
        assertThat(result.get().getEventId()).isEqualTo("event-123");
    }

    @Test
    @DisplayName("Should get events by type")
    void testGetEventsByType() {
        List<EventStore> events = Arrays.asList(eventStore);
        when(eventStoreRepository.findByEventType("OrderCreated")).thenReturn(events);

        List<EventStore> result = eventSourcingService.getEventsByType("OrderCreated");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getEventType()).isEqualTo("OrderCreated");
    }

    @Test
    @DisplayName("Should get events between dates")
    void testGetEventsBetween() {
        LocalDateTime from = LocalDateTime.now().minusHours(1);
        LocalDateTime to = LocalDateTime.now();
        List<EventStore> events = Arrays.asList(eventStore);
        when(eventStoreRepository.findEventsBetween(from, to)).thenReturn(events);

        List<EventStore> result = eventSourcingService.getEventsBetween(from, to);

        assertThat(result).isNotEmpty();
    }

    @Test
    @DisplayName("Should get events by correlation ID")
    void testGetEventsByCorrelationId() {
        List<EventStore> events = Arrays.asList(eventStore);
        when(eventStoreRepository.findByCorrelationId("corr-123")).thenReturn(events);

        List<EventStore> result = eventSourcingService.getEventsByCorrelationId("corr-123");

        assertThat(result).isNotEmpty();
    }

    @Test
    @DisplayName("Should get event by ID")
    void testGetEventById() {
        when(eventStoreRepository.findByEventId("event-123")).thenReturn(Optional.of(eventStore));

        Optional<EventStore> result = eventSourcingService.getEventById("event-123");

        assertThat(result).isPresent();
        assertThat(result.get().getEventId()).isEqualTo("event-123");
    }

    @Test
    @DisplayName("Should get event count by aggregate ID")
    void testGetEventCount() {
        when(eventStoreRepository.countByAggregateId("order-456")).thenReturn(5L);

        long count = eventSourcingService.getEventCount("order-456");

        assertThat(count).isEqualTo(5L);
    }

    @Test
    @DisplayName("Should throw exception when deserialization fails")
    void testDeserializationFailure() throws Exception {
        List<EventStore> events = Arrays.asList(eventStore);
        when(eventStoreRepository.findByAggregateId("order-456")).thenReturn(events);
        when(objectMapper.readValue("{}", OrderCreatedEvent.class)).thenThrow(new RuntimeException("Deserialization error"));

        assertThatThrownBy(() -> eventSourcingService.getEventsByAggregateId("order-456", OrderCreatedEvent.class))
            .isInstanceOf(EventSourcingException.class);
    }

    @Test
    @DisplayName("Should handle empty event list")
    void testEmptyEventList() {
        when(eventStoreRepository.findByAggregateId("nonexistent")).thenReturn(Arrays.asList());

        List<EventStore> result = eventSourcingService.getEventHistoryByAggregateId("nonexistent");

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("Should handle null correlation ID")
    void testStoreEventNullCorrelationId() throws Exception {
        when(objectMapper.writeValueAsString(testEvent)).thenReturn("{}");
        when(eventStoreRepository.save(any(EventStore.class))).thenReturn(eventStore);

        eventSourcingService.storeEvent(testEvent, null, null);

        verify(eventStoreRepository, times(1)).save(any(EventStore.class));
    }

    @Test
    @DisplayName("Should get zero event count")
    void testGetZeroEventCount() {
        when(eventStoreRepository.countByAggregateId("nonexistent")).thenReturn(0L);

        long count = eventSourcingService.getEventCount("nonexistent");

        assertThat(count).isEqualTo(0L);
    }
}
