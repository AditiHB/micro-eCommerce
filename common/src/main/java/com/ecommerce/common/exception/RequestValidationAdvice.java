package com.ecommerce.common.exception;

import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Constraint violations on simple request parameters (for example {@code @RequestParam @Positive int quantity})
 * are client errors: answer 400, not 500. Runs before each service's catch-all handler.
 */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
@Slf4j
public class RequestValidationAdvice {

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ProblemDetail> handleConstraintViolation(ConstraintViolationException ex) {
        Map<String, String> errors = new LinkedHashMap<>();
        ex.getConstraintViolations().forEach(v -> {
            String path = v.getPropertyPath().toString();
            errors.putIfAbsent(path.substring(path.lastIndexOf('.') + 1), v.getMessage());
        });
        log.warn("Invalid request parameter: {}", errors);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Problems.of(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "Invalid request parameter", errors));
    }
}
