package com.ecommerce.common.exception;

import org.springframework.http.HttpStatus;

/** The request is valid but clashes with the current state of the resource (409): an illegal state transition, a duplicate. */
public class ConflictException extends BusinessException {

    public ConflictException(String message) {
        super(message, "CONFLICT");
    }

    public ConflictException(String message, String errorCode) {
        super(message, errorCode);
    }

    @Override
    public HttpStatus getHttpStatus() {
        return HttpStatus.CONFLICT;
    }
}
