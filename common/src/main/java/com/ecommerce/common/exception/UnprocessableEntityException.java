package com.ecommerce.common.exception;

import org.springframework.http.HttpStatus;

/** The request is well-formed but refers to something that does not exist or breaks a rule (422): an unknown product, a reused idempotency key. */
public class UnprocessableEntityException extends BusinessException {

    public UnprocessableEntityException(String message) {
        super(message, "UNPROCESSABLE_ENTITY");
    }

    public UnprocessableEntityException(String message, String errorCode) {
        super(message, errorCode);
    }

    @Override
    public HttpStatus getHttpStatus() {
        return HttpStatus.UNPROCESSABLE_ENTITY;
    }
}
