package com.ecommerce.apigateway;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("FallbackController Unit Tests")
class FallbackControllerTest {

    @InjectMocks
    private FallbackController fallbackController;

    @BeforeEach
    void setUp() {
    }

    @Test
    @DisplayName("Should return fallback response")
    void testFallbackResponse() {
        ResponseEntity<?> response = fallbackController.fallback();

        assertThat(response).isNotNull();
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    }

    @Test
    @DisplayName("Should handle circuit breaker open scenario")
    void testCircuitBreakerOpen() {
        ResponseEntity<?> response = fallbackController.fallback();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    }

    @Test
    @DisplayName("Should return error message in fallback")
    void testFallbackErrorMessage() {
        ResponseEntity<?> response = fallbackController.fallback();

        assertThat(response.getBody()).isNotNull();
    }

    @Test
    @DisplayName("Should indicate service unavailable")
    void testServiceUnavailable() {
        ResponseEntity<?> response = fallbackController.fallback();

        assertThat(response.getStatusCode().is5xxServerError()).isTrue();
    }
}
