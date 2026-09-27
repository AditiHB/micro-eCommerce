package com.ecommerce.common.events;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.UUID;

public abstract class DomainEvent implements Serializable {
    private static final long serialVersionUID = 1L;

    protected String eventId;
    protected LocalDateTime occurredAt;
    protected String aggregateId;
    protected String aggregateType;
    protected int version;

    protected DomainEvent() {
        this.eventId = UUID.randomUUID().toString();
        this.occurredAt = LocalDateTime.now();
        this.version = 1;
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

    public LocalDateTime getOccurredAt() {
        return occurredAt;
    }

    public void setOccurredAt(LocalDateTime occurredAt) {
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

    public int getVersion() {
        return version;
    }

    public void setVersion(int version) {
        this.version = version;
    }

    public String getEventType() {
        return this.getClass().getSimpleName();
    }
}
