package com.ecommerce.common.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;

/**
 * Turns an {@link AccessDeniedException} raised inside a controller (method security such as
 * {@code @PreAuthorize}) into a 403 problem. Without this, each service's catch-all handler would report it
 * as a 500. Runs before that catch-all.
 */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
@Slf4j
public class SecurityExceptionAdvice {

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ProblemDetail> handleAccessDenied(AccessDeniedException ex, WebRequest request) {
        log.warn("Access denied: {}", request.getDescription(false));
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(Problems.of(HttpStatus.FORBIDDEN, "ACCESS_DENIED", "Access denied"));
    }
}
