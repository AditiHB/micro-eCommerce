package com.ecommerce.common.eventsourcing;

public class EventSourcingException extends RuntimeException {
    public EventSourcingException(String message) {
        super(message);
    }

    public EventSourcingException(String message, Throwable cause) {
        super(message, cause);
    }
}
