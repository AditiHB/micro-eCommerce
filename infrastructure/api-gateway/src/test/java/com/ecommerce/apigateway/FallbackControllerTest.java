package com.ecommerce.apigateway;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("FallbackController")
class FallbackControllerTest {

    private final FallbackController controller = new FallbackController();

    @Test
    @DisplayName("an open circuit breaker answers 503 with Retry-After")
    void serviceUnavailable() {
        ResponseEntity<ProblemDetail> response = controller.serviceFallback();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getHeaders().getFirst(HttpHeaders.RETRY_AFTER)).isEqualTo("30");
    }

    @Test
    @DisplayName("the body is an RFC 9457 problem with the platform's error code, like every service")
    void problemBody() {
        ProblemDetail problem = controller.serviceFallback().getBody();

        assertThat(problem).isNotNull();
        assertThat(problem.getStatus()).isEqualTo(503);
        assertThat(problem.getType().toString()).isEqualTo("urn:ecommerce:problem:dependency-unavailable");
        assertThat(problem.getProperties()).containsEntry("errorCode", "DEPENDENCY_UNAVAILABLE").containsKey("timestamp");
    }
}
