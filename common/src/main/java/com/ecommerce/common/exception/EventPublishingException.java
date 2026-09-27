package com.ecommerce.common.exception;

public class EventPublishingException extends BusinessException {
    public EventPublishingException(String message) {
        super(message, "EVENT_PUBLISHING_ERROR");
    }

    public EventPublishingException(String message, Throwable cause) {
        super(message, "EVENT_PUBLISHING_ERROR", cause);
    }
}
