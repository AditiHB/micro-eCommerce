package com.ecommerce.customerservice.exception;

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
import org.springframework.security.authentication.BadCredentialsException;
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
        when(webRequest.getDescription(false)).thenReturn("uri=/api/test");
    }

    @Test
    @DisplayName("Should handle ResourceNotFoundException")
    void testHandleResourceNotFoundException() {
        ResourceNotFoundException exception = new ResourceNotFoundException("Customer", 1L);

        ResponseEntity<ErrorResponse> response = exceptionHandler.handleResourceNotFound(exception, webRequest);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getMessage()).contains("not found");
    }

    @Test
    @DisplayName("Should handle ValidationException")
    void testHandleValidationException() {
        ValidationException exception = new ValidationException("Invalid customer data");

        ResponseEntity<ErrorResponse> response = exceptionHandler.handleBusinessException(exception, webRequest);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getMessage()).contains("Invalid customer data");
    }

    @Test
    @DisplayName("Should handle a failed login (AuthenticationException) as a clean 400, not a 500")
    void testHandleAuthenticationException() {
        BadCredentialsException exception = new BadCredentialsException("Bad credentials");

        ResponseEntity<ErrorResponse> response = exceptionHandler.handleAuthenticationException(exception, webRequest);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getErrorCode()).isEqualTo("INVALID_CREDENTIALS");
        // Deliberately generic - must not reveal whether the username exists.
        assertThat(response.getBody().getMessage()).doesNotContain("Bad credentials");
    }

    @Test
    @DisplayName("Should handle general Exception")
    void testHandleGeneralException() {
        Exception exception = new RuntimeException("Internal error");

        ResponseEntity<ErrorResponse> response = exceptionHandler.handleGenericException(exception, webRequest);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).isNotNull();
    }

    @Test
    @DisplayName("Should include timestamp in error response")
    void testErrorResponseIncludesTimestamp() {
        ValidationException exception = new ValidationException("Test error");

        ResponseEntity<ErrorResponse> response = exceptionHandler.handleBusinessException(exception, webRequest);

        assertThat(response.getBody().getTimestamp()).isNotNull();
    }

    @Test
    @DisplayName("Should include status code in error response")
    void testErrorResponseIncludesStatusCode() {
        ValidationException exception = new ValidationException("Test error");

        ResponseEntity<ErrorResponse> response = exceptionHandler.handleBusinessException(exception, webRequest);

        assertThat(response.getBody().getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
    }
}
