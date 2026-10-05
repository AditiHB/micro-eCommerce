package com.ecommerce.common.eventsourcing;

import com.ecommerce.common.events.DomainEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class EventSourcingService {

    private final EventStoreRepository eventStoreRepository;
    private final ObjectMapper objectMapper;

    public void storeEvent(DomainEvent event) {
        storeEvent(event, null, null);
    }

    public void storeEvent(DomainEvent event, String correlationId, String causationId) {
        try {
            String eventData = objectMapper.writeValueAsString(event);

            EventStore eventStore = EventStore.builder()
                .eventId(event.getEventId())
                .aggregateId(event.getAggregateId())
                .aggregateType(event.getAggregateType())
                .eventType(event.getEventType())
                .eventData(eventData)
                .version(event.getVersion())
                .occurredAt(LocalDateTime.ofInstant(event.getOccurredAt(), ZoneOffset.UTC))
                .correlationId(correlationId)
                .causationId(causationId)
                .build();

            eventStoreRepository.save(eventStore);
            log.info("Event stored: type={}, aggregateId={}, eventId={}",
                event.getEventType(), event.getAggregateId(), event.getEventId());
        } catch (Exception e) {
            log.error("Failed to store event: {}", event.getEventType(), e);
            throw new EventSourcingException("Failed to store event", e);
        }
    }

    @SuppressWarnings("unchecked")
    public <T extends DomainEvent> List<T> getEventsByAggregateId(String aggregateId, Class<T> eventClass) {
        List<EventStore> events = eventStoreRepository.findByAggregateId(aggregateId);
        return (List<T>) events.stream()
            .map(e -> deserializeEvent(e.getEventData(), eventClass))
            .toList();
    }

    public List<EventStore> getEventHistoryByAggregateId(String aggregateId) {
        return eventStoreRepository.findByAggregateId(aggregateId);
    }

    public List<EventStore> getEventsBySince(String aggregateId, int version) {
        return eventStoreRepository.findEventsSince(aggregateId, version);
    }

    public Optional<EventStore> getLatestEvent(String aggregateId, String aggregateType) {
        return eventStoreRepository.findLatestEventForAggregate(aggregateId, aggregateType);
    }

    public List<EventStore> getEventsByType(String eventType) {
        return eventStoreRepository.findByEventType(eventType);
    }

    public List<EventStore> getEventsBetween(LocalDateTime from, LocalDateTime to) {
        return eventStoreRepository.findEventsBetween(from, to);
    }

    public List<EventStore> getEventsByCorrelationId(String correlationId) {
        return eventStoreRepository.findByCorrelationId(correlationId);
    }

    public Optional<EventStore> getEventById(String eventId) {
        return eventStoreRepository.findByEventId(eventId);
    }

    public long getEventCount(String aggregateId) {
        return eventStoreRepository.countByAggregateId(aggregateId);
    }

    @SuppressWarnings("unchecked")
    private <T extends DomainEvent> T deserializeEvent(String eventData, Class<T> eventClass) {
        try {
            return objectMapper.readValue(eventData, eventClass);
        } catch (Exception e) {
            log.error("Failed to deserialize event data", e);
            throw new EventSourcingException("Failed to deserialize event", e);
        }
    }
}
