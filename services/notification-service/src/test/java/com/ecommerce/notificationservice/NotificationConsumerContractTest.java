package com.ecommerce.notificationservice;

import com.ecommerce.common.config.JacksonConfig;
import com.ecommerce.common.events.OrderCreatedEvent;
import com.ecommerce.common.events.PaymentFailedEvent;
import com.ecommerce.common.events.PaymentProcessedEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Consumer-driven contract: what the notification service needs from the events it consumes. Because it no
 * longer asks the order service anything, every event must carry the {@code customerId} on its own - this test
 * fails the build if a producer drops it.
 */
@DisplayName("Notification service as a consumer of saga events")
class NotificationConsumerContractTest {

    private static final ObjectMapper MAPPER = JacksonConfig.newObjectMapper();

    private static <T> T read(String type, Class<T> eventClass) throws IOException {
        try (InputStream in = NotificationConsumerContractTest.class.getClassLoader().getResourceAsStream("contracts/" + type + ".v1.json")) {
            assertThat(in).as("golden contract for %s", type).isNotNull();
            return MAPPER.readValue(in, eventClass);
        }
    }

    @Test
    @DisplayName("order.created: customer, order, the lines and the total to describe")
    void orderCreated() throws IOException {
        OrderCreatedEvent event = read("order.created", OrderCreatedEvent.class);

        assertThat(event.getEventId()).isNotBlank();
        assertThat(event.getCustomerId()).isNotNull();
        assertThat(event.getOrderId()).isNotNull();
        assertThat(event.getLines()).isNotEmpty();
        assertThat(event.getTotalAmount()).isNotNull();
        assertThat(event.getCurrency()).isNotBlank();
    }

    @Test
    @DisplayName("payment.processed: customer, order, amount and currency - no order lookup needed")
    void paymentProcessed() throws IOException {
        PaymentProcessedEvent event = read("payment.processed", PaymentProcessedEvent.class);

        assertThat(event.getCustomerId()).isNotNull();
        assertThat(event.getOrderId()).isNotNull();
        assertThat(event.getAmount()).isNotNull();
        assertThat(event.getCurrency()).isNotBlank();
    }

    @Test
    @DisplayName("payment.failed: customer, order and the reason to tell them")
    void paymentFailed() throws IOException {
        PaymentFailedEvent event = read("payment.failed", PaymentFailedEvent.class);

        assertThat(event.getCustomerId()).isNotNull();
        assertThat(event.getOrderId()).isNotNull();
        assertThat(event.getReason()).isNotBlank();
    }
}
