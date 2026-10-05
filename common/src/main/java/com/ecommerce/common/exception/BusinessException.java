package com.ecommerce.common.exception;

import org.springframework.http.HttpStatus;

/**
 * A rule of the business was broken. Carries a stable machine-readable {@link #getErrorCode() error code} and
 * the HTTP status the API answers with (400 unless a subclass says otherwise), so one advice maps every
 * business failure to a Problem Details response.
 */
public class BusinessException extends RuntimeException {
    private final String errorCode;

    public BusinessException(String message) {
        super(message);
        this.errorCode = "BUSINESS_ERROR";
    }

    public BusinessException(String message, String errorCode) {
        super(message);
        this.errorCode = errorCode;
    }

    public BusinessException(String message, Throwable cause) {
        super(message, cause);
        this.errorCode = "BUSINESS_ERROR";
    }

    public BusinessException(String message, String errorCode, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }

    /** The HTTP status this failure is reported with. */
    public HttpStatus getHttpStatus() {
        return HttpStatus.BAD_REQUEST;
    }
}
