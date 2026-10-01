package com.ecommerce.common.events;

import com.ecommerce.common.eventsourcing.EventSourcingService;
import com.ecommerce.common.exception.EventPublishingException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class EventPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final EventSourcingService eventSourcingService;

    public void publishEvent(DomainEvent event, String topic) {
        publishEvent(event, topic, null, null);
    }

    public void publishEvent(DomainEvent event, String topic, String correlationId, String causationId) {
        try {
            String actualCorrelationId = correlationId != null ? correlationId : UUID.randomUUID().toString();
            String actualCausationId = causationId != null ? causationId : event.getEventId();

            log.debug("Publishing event: type={}, topic={}, correlationId={}",
                event.getEventType(), topic, actualCorrelationId);

            eventSourcingService.storeEvent(event, actualCorrelationId, actualCausationId);

            Message<DomainEvent> message = MessageBuilder
                .withPayload(event)
                .setHeader(KafkaHeaders.TOPIC, topic)
                .setHeader("eventId", event.getEventId())
                .setHeader("eventType", event.getEventType())
                .setHeader("aggregateId", event.getAggregateId())
                .setHeader("aggregateType", event.getAggregateType())
                .setHeader("correlationId", actualCorrelationId)
                .setHeader("causationId", actualCausationId)
                .setHeader("eventTimestamp", System.currentTimeMillis())
                .build();

            kafkaTemplate.send(message)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish event: type={}, topic={}, eventId={}",
                            event.getEventType(), topic, event.getEventId(), ex);
                    } else {
                        log.info("Event published successfully: type={}, topic={}, eventId={}",
                            event.getEventType(), topic, event.getEventId());
                    }
                });
        } catch (Exception e) {
            log.error("Error publishing event: {}", event.getEventType(), e);
            throw new EventPublishingException("Failed to publish event: " + event.getEventType(), e);
        }
    }

    public void publishEventSync(DomainEvent event, String topic) {
        publishEventSync(event, topic, null, null);
    }

    public void publishEventSync(DomainEvent event, String topic, String correlationId, String causationId) {
        try {
            String actualCorrelationId = correlationId != null ? correlationId : UUID.randomUUID().toString();
            String actualCausationId = causationId != null ? causationId : event.getEventId();

            log.debug("Publishing event synchronously: type={}, topic={}, correlationId={}",
                event.getEventType(), topic, actualCorrelationId);

            eventSourcingService.storeEvent(event, actualCorrelationId, actualCausationId);

            Message<DomainEvent> message = MessageBuilder
                .withPayload(event)
                .setHeader(KafkaHeaders.TOPIC, topic)
                .setHeader("eventId", event.getEventId())
                .setHeader("eventType", event.getEventType())
                .setHeader("aggregateId", event.getAggregateId())
                .setHeader("aggregateType", event.getAggregateType())
                .setHeader("correlationId", actualCorrelationId)
                .setHeader("causationId", actualCausationId)
                .setHeader("eventTimestamp", System.currentTimeMillis())
                .build();

            kafkaTemplate.send(message).get();
            log.info("Event published synchronously: type={}, topic={}, eventId={}",
                event.getEventType(), topic, event.getEventId());
        } catch (Exception e) {
            log.error("Error publishing event synchronously: {}", event.getEventType(), e);
            throw new EventPublishingException("Failed to publish event synchronously: " + event.getEventType(), e);
        }
    }
}
