package com.ecommerce.common.exception;

import org.springframework.http.HttpStatus;

/** The caller's {@code If-Match} no longer matches the resource: someone else changed it first (412). */
public class PreconditionFailedException extends BusinessException {

    public PreconditionFailedException(String message) {
        super(message, "PRECONDITION_FAILED");
    }

    public PreconditionFailedException(String message, String errorCode) {
        super(message, errorCode);
    }

    @Override
    public HttpStatus getHttpStatus() {
        return HttpStatus.PRECONDITION_FAILED;
    }
}
