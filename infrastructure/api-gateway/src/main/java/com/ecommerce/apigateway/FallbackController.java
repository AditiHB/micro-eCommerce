package com.ecommerce.apigateway;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.time.Instant;

/**
 * What a caller gets when a route's circuit breaker is open or its time limit ran out: a 503 as an RFC 9457
 * problem document - the same error shape every service uses - with {@code Retry-After}, never an invented
 * success body.
 */
@RestController
@RequestMapping("/fallback")
@Slf4j
public class FallbackController {

    /** Seconds a client should wait before retrying; matches the breaker's open-state wait. */
    static final String RETRY_AFTER_SECONDS = "30";

    @RequestMapping("/service")
    public ResponseEntity<ProblemDetail> serviceFallback() {
        log.warn("Service fallback triggered - circuit breaker open or time limit exceeded");

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE,
                "The requested service is currently unavailable. Please try again shortly.");
        problem.setTitle("Service Unavailable");
        problem.setType(URI.create("urn:ecommerce:problem:dependency-unavailable"));
        problem.setProperty("errorCode", "DEPENDENCY_UNAVAILABLE");
        problem.setProperty("timestamp", Instant.now().toString());
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .header(HttpHeaders.RETRY_AFTER, RETRY_AFTER_SECONDS)
                .body(problem);
    }
}
