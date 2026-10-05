package com.ecommerce.notificationservice.exception;

import com.ecommerce.common.exception.ProblemDetailsAdvice;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Notification-service's error handling: every failure is an RFC 9457 problem document, built by the shared
 * {@link ProblemDetailsAdvice}. Business failures extend {@code BusinessException} and carry their own status
 * and error code, so they need no mapping here; add a handler to this class only for an exception type that
 * only this service knows about.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ProblemDetailsAdvice {
}
