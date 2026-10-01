package com.ecommerce.common.exception;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

@DisplayName("Exception Classes Unit Tests")
class ExceptionTest {

    @Test
    @DisplayName("Should create BusinessException with message and code")
    void testBusinessException() {
        String message = "Test error message";
        String code = "TEST_ERROR";

        BusinessException exception = new BusinessException(message, code);

        assertThat(exception.getMessage()).isEqualTo(message);
        assertThat(exception.getErrorCode()).isEqualTo(code);
    }

    @Test
    @DisplayName("Should create ResourceNotFoundException")
    void testResourceNotFoundException() {
        String resourceName = "Customer";
        Long id = 123L;

        ResourceNotFoundException exception = new ResourceNotFoundException(resourceName, id);

        assertThat(exception.getMessage()).isNotNull();
    }

    @Test
    @DisplayName("Should create ValidationException")
    void testValidationException() {
        String message = "Validation failed";

        ValidationException exception = new ValidationException(message);

        assertThat(exception.getMessage()).isEqualTo(message);
        assertThat(exception.getErrorCode()).isEqualTo("VALIDATION_ERROR");
    }

    @Test
    @DisplayName("Should create EventPublishingException with message and cause")
    void testEventPublishingException() {
        String message = "Event publishing failed";
        RuntimeException cause = new RuntimeException("Kafka error");

        EventPublishingException exception = new EventPublishingException(message, cause);

        assertThat(exception.getMessage()).isEqualTo(message);
        assertThat(exception.getCause()).isEqualTo(cause);
    }

    @Test
    @DisplayName("Should create EventPublishingException with message only")
    void testEventPublishingExceptionMessageOnly() {
        String message = "Event publishing failed";

        EventPublishingException exception = new EventPublishingException(message);

        assertThat(exception.getMessage()).isEqualTo(message);
    }

    @Test
    @DisplayName("Should preserve exception hierarchy")
    void testExceptionHierarchy() {
        BusinessException businessException = new BusinessException("Business error", "ERROR");

        assertThat(businessException).isInstanceOf(RuntimeException.class);
    }

    @Test
    @DisplayName("Should allow null message in BusinessException")
    void testBusinessExceptionNullMessage() {
        BusinessException exception = new BusinessException(null, "ERROR_CODE");

        assertThat(exception.getMessage()).isNull();
        assertThat(exception.getErrorCode()).isEqualTo("ERROR_CODE");
    }

    @Test
    @DisplayName("Should handle empty error code")
    void testBusinessExceptionEmptyCode() {
        BusinessException exception = new BusinessException("Message", "");

        assertThat(exception.getMessage()).isEqualTo("Message");
        assertThat(exception.getErrorCode()).isEmpty();
    }

    @Test
    @DisplayName("Should create exception with special characters in message")
    void testExceptionWithSpecialCharacters() {
        String message = "Error: User@example.com not found! #$%";
        BusinessException exception = new BusinessException(message, "ERROR");

        assertThat(exception.getMessage()).isEqualTo(message);
    }

    @Test
    @DisplayName("Should maintain exception state after creation")
    void testExceptionImmutability() {
        BusinessException exception = new BusinessException("Original message", "ERROR");

        String message = exception.getMessage();
        String code = exception.getErrorCode();

        assertThat(message).isEqualTo("Original message");
        assertThat(code).isEqualTo("ERROR");
    }
}
