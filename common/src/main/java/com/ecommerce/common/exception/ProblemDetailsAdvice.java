package com.ecommerce.common.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The single error contract of the API: every failure, from every service, is an RFC 9457 Problem Details
 * document (see {@link Problems}). Each service has one empty {@code @RestControllerAdvice} subclass of this
 * (its {@code GlobalExceptionHandler}) - the place to map an exception only that service knows about.
 *
 * <p>Spring's own MVC exceptions (malformed JSON, wrong method, missing parameter, type mismatch...) are handled
 * by the {@link ResponseEntityExceptionHandler} parent and enriched with the same members, so a client parses
 * one error shape no matter what went wrong.
 */
@Slf4j
public abstract class ProblemDetailsAdvice extends ResponseEntityExceptionHandler {

    /** Every deliberate business failure carries its own status and code (see {@link BusinessException}). */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<Object> handleBusiness(BusinessException ex, WebRequest request) {
        HttpStatus status = ex.getHttpStatus();
        if (status.is5xxServerError()) {
            log.error("Business failure {}: {}", ex.getErrorCode(), ex.getMessage(), ex);
        } else {
            log.warn("Business rule rejected the request ({}): {}", ex.getErrorCode(), ex.getMessage());
        }
        ProblemDetail problem = Problems.of(status, ex.getErrorCode(), ex.getMessage());
        return handleExceptionInternal(ex, problem, new HttpHeaders(), status, request);
    }

    /** Someone else changed the record between this caller's read and write. */
    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<Object> handleOptimisticLock(OptimisticLockingFailureException ex, WebRequest request) {
        log.warn("Concurrent modification: {}", ex.getMessage());
        ProblemDetail problem = Problems.of(HttpStatus.CONFLICT, "CONCURRENT_MODIFICATION",
                "The resource was modified by someone else. Re-read it and try again.");
        return handleExceptionInternal(ex, problem, new HttpHeaders(), HttpStatus.CONFLICT, request);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Object> handleDataIntegrity(DataIntegrityViolationException ex, WebRequest request) {
        log.warn("Data integrity violation: {}", ex.getMostSpecificCause().getMessage());
        ProblemDetail problem = Problems.of(HttpStatus.CONFLICT, "DATA_CONFLICT",
                "The request conflicts with data that already exists.");
        return handleExceptionInternal(ex, problem, new HttpHeaders(), HttpStatus.CONFLICT, request);
    }

    /** Last resort: log everything, tell the caller nothing about the internals. */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Object> handleUnexpected(Exception ex, WebRequest request) {
        log.error("Unexpected exception", ex);
        ProblemDetail problem = Problems.of(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR",
                "An unexpected error occurred");
        return handleExceptionInternal(ex, problem, new HttpHeaders(), HttpStatus.INTERNAL_SERVER_ERROR, request);
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex, HttpHeaders headers,
                                                                  HttpStatusCode status, WebRequest request) {
        Map<String, String> errors = new LinkedHashMap<>();
        ex.getBindingResult().getAllErrors().forEach(error -> {
            String field = error instanceof FieldError fe ? fe.getField() : error.getObjectName();
            errors.putIfAbsent(field, error.getDefaultMessage());
        });
        log.warn("Validation failed: {}", errors);
        ProblemDetail problem = Problems.of(status, "VALIDATION_FAILED", "Validation failed", errors);
        return handleExceptionInternal(ex, problem, headers, status, request);
    }

    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(HttpMessageNotReadableException ex, HttpHeaders headers,
                                                                  HttpStatusCode status, WebRequest request) {
        ProblemDetail problem = Problems.of(status, "MALFORMED_REQUEST", "The request body is missing or malformed");
        return handleExceptionInternal(ex, problem, headers, status, request);
    }

    /** Adds the common members to every problem Spring itself produced (405, 415, missing parameter, ...). */
    @Override
    protected ResponseEntity<Object> createResponseEntity(Object body, HttpHeaders headers, HttpStatusCode statusCode,
                                                          WebRequest request) {
        if (body instanceof ProblemDetail problem
                && (problem.getProperties() == null || !problem.getProperties().containsKey("errorCode"))) {
            Problems.enrich(problem, Problems.codeFor(statusCode), null);
        }
        return super.createResponseEntity(body, headers, statusCode, request);
    }
}
