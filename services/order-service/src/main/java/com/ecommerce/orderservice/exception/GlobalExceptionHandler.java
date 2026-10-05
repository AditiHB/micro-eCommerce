package com.ecommerce.orderservice.exception;

import com.ecommerce.common.exception.ProblemDetailsAdvice;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Order-service's error handling: every failure is an RFC 9457 problem document, built by the shared
 * {@link ProblemDetailsAdvice}. Order-specific failures extend {@code BusinessException}
 * ({@link InvalidOrderTransitionException} is a 409) and need no mapping here; add a handler to this class only
 * for an exception type that only this service knows about.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ProblemDetailsAdvice {
}
