package com.ecommerce.common.eventsourcing;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

@DisplayName("EventSourcingException Tests")
class EventSourcingExceptionTest {

    @Test
    @DisplayName("Should create exception with message")
    void testExceptionWithMessage() {
        String message = "Event sourcing failed";
        EventSourcingException exception = new EventSourcingException(message);

        assertThat(exception.getMessage()).isEqualTo(message);
    }

    @Test
    @DisplayName("Should create exception with message and cause")
    void testExceptionWithMessageAndCause() {
        String message = "Event sourcing failed";
        Throwable cause = new RuntimeException("Database error");
        EventSourcingException exception = new EventSourcingException(message, cause);

        assertThat(exception.getMessage()).isEqualTo(message);
        assertThat(exception.getCause()).isEqualTo(cause);
    }

    @Test
    @DisplayName("Should create exception with cause only")
    void testExceptionWithCause() {
        Throwable cause = new RuntimeException("Database error");
        EventSourcingException exception = new EventSourcingException(cause);

        assertThat(exception.getCause()).isEqualTo(cause);
    }

    @Test
    @DisplayName("Should preserve exception hierarchy")
    void testExceptionHierarchy() {
        EventSourcingException exception = new EventSourcingException("Test error");

        assertThat(exception).isInstanceOf(Exception.class);
    }

    @Test
    @DisplayName("Should handle null message")
    void testNullMessage() {
        EventSourcingException exception = new EventSourcingException((String) null);

        assertThat(exception.getMessage()).isNull();
    }

    @Test
    @DisplayName("Should handle empty message")
    void testEmptyMessage() {
        EventSourcingException exception = new EventSourcingException("");

        assertThat(exception.getMessage()).isEmpty();
    }
}
