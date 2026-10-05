package com.ecommerce.common.events;

import com.ecommerce.common.testsupport.EventSamples;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("DomainEvent")
class DomainEventTest {

    @Test
    @DisplayName("a new event gets a unique id, a UTC timestamp and its schema version")
    void newEventDefaults() {
        OrderCreatedEvent first = new OrderCreatedEvent(1L, 2L, List.of(), BigDecimal.ZERO, "USD");
        OrderCreatedEvent second = new OrderCreatedEvent(1L, 2L, List.of(), BigDecimal.ZERO, "USD");

        assertThat(first.getEventId()).isNotBlank().isNotEqualTo(second.getEventId());
        assertThat(first.getOccurredAt()).isBeforeOrEqualTo(Instant.now());
        assertThat(first.getVersion()).isEqualTo(1);
        assertThat(first.getEventType()).isEqualTo("order.created");
    }

    @Test
    @DisplayName("the aggregate is the order, so every event of one order shares one Kafka key")
    void sagaEventsOfOneOrderShareTheirKey() {
        assertThat(EventSamples.all().values()).allSatisfy(event ->
                assertThat(event.getAggregateId()).as(event.getEventType()).isEqualTo("42"));
    }

    @Test
    @DisplayName("order lines carry their priced total")
    void lineTotals() {
        OrderCreatedEvent event = EventSamples.orderCreated();

        assertThat(event.getLines()).extracting(LineItem::getLineTotal)
                .containsExactly(new BigDecimal("159.98"), new BigDecimal("12.99"));
        assertThat(event.getTotalAmount()).isEqualByComparingTo("172.97");
    }

    @Test
    @DisplayName("an undeclared event class falls back to its simple name and has no topic")
    void undeclaredEvent() {
        class Plain extends DomainEvent {
        }

        assertThat(new Plain().getEventType()).isEqualTo("Plain");
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> EventCatalog.topicOf(Plain.class))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
