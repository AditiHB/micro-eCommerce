package com.ecommerce.common.events;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.UUID;

/**
 * Base of every event that crosses a service boundary.
 *
 * <p>Wire format is JSON, never Java serialization. Timestamps are UTC instants (ISO-8601 with a zone), and
 * readers ignore fields they do not know, so a producer can add an optional field without breaking consumers
 * that have not been rebuilt yet (see {@link EventSchema} and {@code docs/EVENT_CONTRACTS.md}).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public abstract class DomainEvent {

    protected String eventId;
    protected Instant occurredAt;
    protected String aggregateId;
    protected String aggregateType;
    protected int version;

    protected DomainEvent() {
        this.eventId = UUID.randomUUID().toString();
        this.occurredAt = Instant.now();
        this.version = EventCatalog.versionOf(getClass());
    }

    protected DomainEvent(String aggregateId, String aggregateType) {
        this();
        this.aggregateId = aggregateId;
        this.aggregateType = aggregateType;
    }

    public String getEventId() {
        return eventId;
    }

    public void setEventId(String eventId) {
        this.eventId = eventId;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public void setOccurredAt(Instant occurredAt) {
        this.occurredAt = occurredAt;
    }

    public String getAggregateId() {
        return aggregateId;
    }

    public void setAggregateId(String aggregateId) {
        this.aggregateId = aggregateId;
    }

    public String getAggregateType() {
        return aggregateType;
    }

    public void setAggregateType(String aggregateType) {
        this.aggregateType = aggregateType;
    }

    /** Schema version of this event type (not the aggregate's version). */
    public int getVersion() {
        return version;
    }

    public void setVersion(int version) {
        this.version = version;
    }

    /** Logical type name, e.g. {@code order.created}. Informational on the wire; the type header is what routes. */
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    public String getEventType() {
        return EventCatalog.typeOf(getClass());
    }
}
