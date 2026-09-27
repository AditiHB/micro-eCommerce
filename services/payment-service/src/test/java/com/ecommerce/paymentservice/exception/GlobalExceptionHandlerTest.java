package com.ecommerce.paymentservice.exception;

import com.ecommerce.common.dto.ErrorResponse;
import com.ecommerce.common.exception.ResourceNotFoundException;
import com.ecommerce.common.exception.ValidationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.context.request.WebRequest;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("GlobalExceptionHandler Unit Tests")
class GlobalExceptionHandlerTest {

    @InjectMocks
    private GlobalExceptionHandler exceptionHandler;

    private WebRequest webRequest;

    @BeforeEach
    void setUp() {
        webRequest = mock(WebRequest.class);
    }

    @Test
    @DisplayName("Should handle ResourceNotFoundException")
    void testHandleResourceNotFoundException() {
        ResourceNotFoundException exception = new ResourceNotFoundException("Payment", 1L);

        ResponseEntity<ErrorResponse> response = exceptionHandler.handleResourceNotFoundException(exception, webRequest);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getMessage()).contains("not found");
    }

    @Test
    @DisplayName("Should handle ValidationException")
    void testHandleValidationException() {
        ValidationException exception = new ValidationException("Invalid payment data");

        ResponseEntity<ErrorResponse> response = exceptionHandler.handleValidationException(exception, webRequest);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getMessage()).contains("Invalid payment data");
    }

    @Test
    @DisplayName("Should handle general Exception")
    void testHandleGeneralException() {
        Exception exception = new RuntimeException("Internal error");

        ResponseEntity<ErrorResponse> response = exceptionHandler.handleGlobalException(exception, webRequest);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).isNotNull();
    }

    @Test
    @DisplayName("Should include HTTP status code in error response")
    void testErrorResponseIncludesStatusCode() {
        ValidationException exception = new ValidationException("Invalid amount");

        ResponseEntity<ErrorResponse> response = exceptionHandler.handleValidationException(exception, webRequest);

        assertThat(response.getBody().getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
    }
}
