package com.ecommerce.common.exception;

import org.springframework.http.HttpStatus;

public class EventPublishingException extends BusinessException {
    public EventPublishingException(String message) {
        super(message, "EVENT_PUBLISHING_ERROR");
    }

    public EventPublishingException(String message, Throwable cause) {
        super(message, "EVENT_PUBLISHING_ERROR", cause);
    }

    /** Failing to record an event is the server's problem, not the caller's. */
    @Override
    public HttpStatus getHttpStatus() {
        return HttpStatus.INTERNAL_SERVER_ERROR;
    }
}
