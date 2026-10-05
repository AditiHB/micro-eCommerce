package com.ecommerce.common.exception;

/**
 * An event that can never be processed no matter how often it is retried (a contract violation, a reference
 * that cannot exist). The error handler dead-letters it immediately instead of burning the retry budget.
 */
public class NonRetryableEventException extends RuntimeException {

    public NonRetryableEventException(String message) {
        super(message);
    }

    public NonRetryableEventException(String message, Throwable cause) {
        super(message, cause);
    }
}
