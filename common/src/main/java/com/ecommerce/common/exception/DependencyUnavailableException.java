package com.ecommerce.common.exception;

import org.springframework.http.HttpStatus;

/** A service this request depends on is down or too slow (503). Safe to retry later. */
public class DependencyUnavailableException extends BusinessException {

    public DependencyUnavailableException(String message) {
        super(message, "DEPENDENCY_UNAVAILABLE");
    }

    public DependencyUnavailableException(String message, String errorCode) {
        super(message, errorCode);
    }

    @Override
    public HttpStatus getHttpStatus() {
        return HttpStatus.SERVICE_UNAVAILABLE;
    }
}
