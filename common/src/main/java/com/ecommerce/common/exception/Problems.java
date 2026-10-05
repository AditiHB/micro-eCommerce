package com.ecommerce.common.exception;

import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;

import java.net.URI;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;

/**
 * Builds the one error shape every service returns: an RFC 9457 Problem Details document
 * ({@code application/problem+json}) with these extension members -
 * {@code errorCode} (stable, machine-readable), {@code timestamp}, {@code traceId} (to find the request in the
 * logs) and, for validation failures, {@code errors} (field to message).
 */
public final class Problems {

    private static final String TYPE_PREFIX = "urn:ecommerce:problem:";

    private Problems() {
    }

    public static ProblemDetail of(HttpStatusCode status, String errorCode, String detail) {
        return of(status, errorCode, detail, null);
    }

    public static ProblemDetail of(HttpStatusCode status, String errorCode, String detail, Map<String, String> errors) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        return enrich(problem, errorCode, errors);
    }

    /** Adds the common members to a problem Spring built itself (for the standard MVC exceptions). */
    public static ProblemDetail enrich(ProblemDetail problem, String errorCode, Map<String, String> errors) {
        problem.setType(URI.create(TYPE_PREFIX + errorCode.toLowerCase(Locale.ROOT).replace('_', '-')));
        if (problem.getTitle() == null) {
            HttpStatus resolved = HttpStatus.resolve(problem.getStatus());
            problem.setTitle(resolved != null ? resolved.getReasonPhrase() : null);
        }
        problem.setProperty("errorCode", errorCode);
        problem.setProperty("timestamp", Instant.now().toString());
        String traceId = MDC.get("traceId");
        if (traceId != null) {
            problem.setProperty("traceId", traceId);
        }
        if (errors != null && !errors.isEmpty()) {
            problem.setProperty("errors", errors);
        }
        return problem;
    }

    /** A default error code for a status with no more specific one, e.g. 405 -> METHOD_NOT_ALLOWED. */
    public static String codeFor(HttpStatusCode status) {
        HttpStatus resolved = HttpStatus.resolve(status.value());
        return resolved == null ? "ERROR_" + status.value() : resolved.name();
    }
}
