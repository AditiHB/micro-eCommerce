package com.ecommerce.common.events;

import com.ecommerce.common.config.JacksonConfig;
import com.ecommerce.common.eventsourcing.EventSourcingService;
import com.ecommerce.common.exception.EventPublishingException;
import com.ecommerce.common.outbox.OutboxEvent;
import com.ecommerce.common.outbox.OutboxRepository;
import com.ecommerce.common.testsupport.EventSamples;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/** Unit-level behaviour of the outbox write side; the transactional guarantees are in OutboxIntegrationTest. */
@ExtendWith(MockitoExtension.class)
@DisplayName("EventPublisher (outbox writer)")
class EventPublisherTest {

    @Mock
    private OutboxRepository outbox;
    @Mock
    private EventSourcingService eventSourcing;

    private final ObjectMapper mapper = JacksonConfig.newObjectMapper();
    private EventPublisher publisher;

    @BeforeEach
    void setUp() {
        publisher = new EventPublisher(outbox, eventSourcing, mapper);
    }

    @Test
    @DisplayName("queues the event for its own topic, keyed by aggregate, as a pending outbox row")
    void queuesForTheEventsTopic() throws Exception {
        OrderCreatedEvent event = EventSamples.orderCreated();

        publisher.publish(event, "corr-1", "cause-1");

        ArgumentCaptor<OutboxEvent> row = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outbox).save(row.capture());
        OutboxEvent saved = row.getValue();
        assertThat(saved.getTopic()).isEqualTo(Topics.ORDER_CREATED);
        assertThat(saved.getMessageKey()).isEqualTo("42");
        assertThat(saved.getEventId()).isEqualTo(event.getEventId());
        assertThat(saved.getEventType()).isEqualTo("order.created");
        assertThat(saved.getStatus()).isEqualTo(OutboxEvent.PENDING);
        assertThat(saved.getAttempts()).isZero();
        assertThat(saved.getCorrelationId()).isEqualTo("corr-1");
        assertThat(saved.getCausationId()).isEqualTo("cause-1");
        assertThat(mapper.readTree(saved.getPayload()).get("totalAmount").decimalValue()).isEqualByComparingTo("172.97");
    }

    @Test
    @DisplayName("also appends the event to the event store, the permanent audit history")
    void appendsToEventStore() {
        OrderCancelledEvent event = EventSamples.orderCancelled();

        publisher.publish(event);

        verify(eventSourcing).storeEvent(eq(event), any(String.class), eq(event.getEventId()));
    }

    @Test
    @DisplayName("starts a new correlation id when none is given and defaults causation to the event itself")
    void defaultsCorrelationAndCausation() {
        publisher.publish(EventSamples.paymentFailed());

        ArgumentCaptor<OutboxEvent> row = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outbox).save(row.capture());
        assertThat(row.getValue().getCorrelationId()).isNotBlank();
        assertThat(row.getValue().getCausationId()).isEqualTo(EventSamples.paymentFailed().getEventId());
    }

    @Test
    @DisplayName("an event with no @EventSchema is rejected before anything is written")
    void undeclaredEventIsRejected() {
        class Rogue extends DomainEvent {
        }

        assertThatThrownBy(() -> publisher.publish(new Rogue())).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(outbox, eventSourcing);
    }

    @Test
    @DisplayName("a failure to record the event propagates, so the caller's transaction rolls back")
    void failureToRecordPropagates() {
        doThrow(new IllegalStateException("db down")).when(outbox).save(any());

        assertThatThrownBy(() -> publisher.publish(EventSamples.inventoryFailed()))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("an unserializable event is reported as an EventPublishingException")
    void unserializableEvent() throws Exception {
        ObjectMapper broken = org.mockito.Mockito.mock(ObjectMapper.class);
        org.mockito.Mockito.when(broken.writeValueAsString(any()))
                .thenThrow(new com.fasterxml.jackson.core.JsonProcessingException("boom") { });

        assertThatThrownBy(() -> new EventPublisher(outbox, eventSourcing, broken).publish(EventSamples.orderCreated()))
                .isInstanceOf(EventPublishingException.class);
        verifyNoInteractions(outbox);
    }
}
