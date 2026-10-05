package com.ecommerce.common.events;

import com.ecommerce.common.eventsourcing.EventSourcingService;
import com.ecommerce.common.exception.EventPublishingException;
import com.ecommerce.common.outbox.OutboxEvent;
import com.ecommerce.common.outbox.OutboxRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * Records an event to be published - it does <em>not</em> talk to Kafka.
 *
 * <p>This is the transactional-outbox write side. The event is inserted into the {@code outbox_event} table in
 * the caller's own database transaction ({@link Propagation#MANDATORY} - calling it outside one is a bug and
 * fails loudly), so the business change and "tell everyone about it" commit or roll back together. The
 * {@link com.ecommerce.common.outbox.OutboxRelay} delivers it to Kafka afterwards and keeps retrying until the
 * broker acknowledges it. Events are also appended to the event store, the permanent audit history.
 *
 * <p>The topic comes from the event's own {@link EventSchema}, so a call site cannot publish to the wrong one.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EventPublisher {

    private final OutboxRepository outbox;
    private final EventSourcingService eventSourcingService;
    private final ObjectMapper objectMapper;

    @Transactional(propagation = Propagation.MANDATORY)
    public void publish(DomainEvent event) {
        publish(event, null, null);
    }

    /**
     * @param correlationId ties together every event of one business flow; a new one is started if null
     * @param causationId   the event (or request) that directly caused this one; defaults to this event's own id
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void publish(DomainEvent event, String correlationId, String causationId) {
        String topic = EventCatalog.topicOf(event.getClass());
        String actualCorrelationId = correlationId != null ? correlationId : UUID.randomUUID().toString();
        String actualCausationId = causationId != null ? causationId : event.getEventId();

        String payload;
        try {
            payload = objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            throw new EventPublishingException("Cannot serialize event " + event.getEventType(), e);
        }

        eventSourcingService.storeEvent(event, actualCorrelationId, actualCausationId);

        Instant now = Instant.now();
        outbox.save(OutboxEvent.builder()
                .eventId(event.getEventId())
                .topic(topic)
                .messageKey(event.getAggregateId())
                .eventType(event.getEventType())
                .aggregateId(event.getAggregateId())
                .aggregateType(event.getAggregateType())
                .correlationId(actualCorrelationId)
                .causationId(actualCausationId)
                .payload(payload)
                .status(OutboxEvent.PENDING)
                .attempts(0)
                .nextAttemptAt(now)
                .createdAt(now)
                .build());

        log.debug("Queued {} ({}) for topic {}", event.getEventType(), event.getEventId(), topic);
    }
}
