package com.ecommerce.common.events;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("DlqPublisher Unit Tests")
class DlqPublisherTest {

    @Mock
    private KafkaTemplate<String, String> dlqKafkaTemplate;

    private DlqPublisher dlqPublisher;

    private OrderCreatedEvent testEvent;

    @BeforeEach
    void setUp() {
        // Constructed manually (not @InjectMocks) so a real ObjectMapper actually
        // serializes testEvent, rather than Mockito's constructor-injection only
        // wiring in @Mock-annotated fields and leaving this one null.
        dlqPublisher = new DlqPublisher(dlqKafkaTemplate, new ObjectMapper().registerModule(new JavaTimeModule()));

        testEvent = new OrderCreatedEvent();
        testEvent.setEventId("event-123");
        testEvent.setAggregateId("order-456");
        testEvent.setAggregateType("Order");
        testEvent.setOrderId(456L);
        testEvent.setCustomerId(789L);
    }

    @Test
    @DisplayName("Should publish the failed event to <topic>-dlq")
    void testPublishRoutesToDlqTopic() {
        CompletableFuture<SendResult<String, String>> future = CompletableFuture.completedFuture(null);
        when(dlqKafkaTemplate.send(eq("order-created-dlq"), eq("event-123"), anyString()))
            .thenReturn(future);

        dlqPublisher.publish(testEvent, "order-created", new RuntimeException("boom"));

        verify(dlqKafkaTemplate).send(eq("order-created-dlq"), eq("event-123"), anyString());
    }

    @Test
    @DisplayName("Should not throw when the DLQ send itself fails asynchronously")
    void testPublishDoesNotThrowOnAsyncFailure() {
        CompletableFuture<SendResult<String, String>> failedFuture = new CompletableFuture<>();
        failedFuture.completeExceptionally(new RuntimeException("Kafka down"));
        when(dlqKafkaTemplate.send(anyString(), anyString(), anyString()))
            .thenReturn(failedFuture);

        assertThatCode(() -> dlqPublisher.publish(testEvent, "order-created", new RuntimeException("boom")))
            .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Should not throw when the template itself throws synchronously")
    void testPublishDoesNotThrowOnSyncFailure() {
        when(dlqKafkaTemplate.send(anyString(), anyString(), anyString()))
            .thenThrow(new RuntimeException("Producer closed"));

        assertThatCode(() -> dlqPublisher.publish(testEvent, "order-created", new RuntimeException("boom")))
            .doesNotThrowAnyException();
    }
}
