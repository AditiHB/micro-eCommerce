package com.ecommerce.common.dto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.*;

@DisplayName("ErrorResponse DTO Tests")
class ErrorResponseTest {

    @Test
    @DisplayName("Should create error response with all fields")
    void testErrorResponseCreation() {
        LocalDateTime now = LocalDateTime.now();
        ErrorResponse response = ErrorResponse.builder()
            .timestamp(now)
            .status(400)
            .error("Bad Request")
            .message("Invalid input")
            .path("/api/test")
            .build();

        assertThat(response.getTimestamp()).isEqualTo(now);
        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(response.getError()).isEqualTo("Bad Request");
        assertThat(response.getMessage()).isEqualTo("Invalid input");
        assertThat(response.getPath()).isEqualTo("/api/test");
    }

    @Test
    @DisplayName("Should update status via setter")
    void testSetStatus() {
        ErrorResponse response = new ErrorResponse();
        response.setStatus(404);

        assertThat(response.getStatus()).isEqualTo(404);
    }

    @Test
    @DisplayName("Should update error message via setter")
    void testSetError() {
        ErrorResponse response = new ErrorResponse();
        response.setError("Not Found");

        assertThat(response.getError()).isEqualTo("Not Found");
    }

    @Test
    @DisplayName("Should update message via setter")
    void testSetMessage() {
        ErrorResponse response = new ErrorResponse();
        response.setMessage("Resource not found");

        assertThat(response.getMessage()).isEqualTo("Resource not found");
    }

    @Test
    @DisplayName("Should update path via setter")
    void testSetPath() {
        ErrorResponse response = new ErrorResponse();
        response.setPath("/api/customers");

        assertThat(response.getPath()).isEqualTo("/api/customers");
    }

    @Test
    @DisplayName("Should update timestamp via setter")
    void testSetTimestamp() {
        LocalDateTime now = LocalDateTime.now();
        ErrorResponse response = new ErrorResponse();
        response.setTimestamp(now);

        assertThat(response.getTimestamp()).isEqualTo(now);
    }

    @Test
    @DisplayName("Should support no-args constructor")
    void testNoArgsConstructor() {
        ErrorResponse response = new ErrorResponse();

        assertThat(response.getStatus()).isEqualTo(0);
        assertThat(response.getError()).isNull();
        assertThat(response.getMessage()).isNull();
        assertThat(response.getPath()).isNull();
    }

    @Test
    @DisplayName("Should handle different error codes")
    void testDifferentErrorCodes() {
        ErrorResponse notFound = ErrorResponse.builder().status(404).build();
        ErrorResponse forbidden = ErrorResponse.builder().status(403).build();
        ErrorResponse serverError = ErrorResponse.builder().status(500).build();

        assertThat(notFound.getStatus()).isEqualTo(404);
        assertThat(forbidden.getStatus()).isEqualTo(403);
        assertThat(serverError.getStatus()).isEqualTo(500);
    }
}
