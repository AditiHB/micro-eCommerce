package com.ecommerce.orderservice;

import com.ecommerce.common.config.JacksonConfig;
import com.ecommerce.common.events.InventoryFailedEvent;
import com.ecommerce.common.events.InventoryReservedEvent;
import com.ecommerce.common.events.PaymentFailedEvent;
import com.ecommerce.common.events.PaymentProcessedEvent;
import com.ecommerce.common.events.RefundCompletedEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Consumer-driven contract: what the order service needs from each event it consumes. It reads the producers'
 * golden files (the published wire format) and asserts that every field it relies on is present, so a producer
 * that renames or drops one fails the build here - in the consumer's own test - instead of in production.
 */
@DisplayName("Order service as a consumer of saga events")
class OrderConsumerContractTest {

    private static final ObjectMapper MAPPER = JacksonConfig.newObjectMapper();

    private static <T> T read(String type, Class<T> eventClass) throws IOException {
        try (InputStream in = OrderConsumerContractTest.class.getClassLoader().getResourceAsStream("contracts/" + type + ".v1.json")) {
            assertThat(in).as("golden contract for %s", type).isNotNull();
            return MAPPER.readValue(in, eventClass);
        }
    }

    @Test
    @DisplayName("inventory.reserved: the order id")
    void inventoryReserved() throws IOException {
        InventoryReservedEvent event = read("inventory.reserved", InventoryReservedEvent.class);

        assertThat(event.getEventId()).isNotBlank();
        assertThat(event.getOrderId()).isNotNull();
    }

    @Test
    @DisplayName("inventory.failed: the order id and a reason to put on the cancellation")
    void inventoryFailed() throws IOException {
        InventoryFailedEvent event = read("inventory.failed", InventoryFailedEvent.class);

        assertThat(event.getEventId()).isNotBlank();
        assertThat(event.getOrderId()).isNotNull();
        assertThat(event.getReason()).isNotBlank();
    }

    @Test
    @DisplayName("payment.processed: the order id, payment id, amount and currency")
    void paymentProcessed() throws IOException {
        PaymentProcessedEvent event = read("payment.processed", PaymentProcessedEvent.class);

        assertThat(event.getEventId()).isNotBlank();
        assertThat(event.getOrderId()).isNotNull();
        assertThat(event.getPaymentId()).isNotNull();
        assertThat(event.getAmount()).isNotNull();
        assertThat(event.getCurrency()).isNotBlank();
    }

    @Test
    @DisplayName("payment.failed: the order id and the decline reason")
    void paymentFailed() throws IOException {
        PaymentFailedEvent event = read("payment.failed", PaymentFailedEvent.class);

        assertThat(event.getEventId()).isNotBlank();
        assertThat(event.getOrderId()).isNotNull();
        assertThat(event.getReason()).isNotBlank();
    }

    @Test
    @DisplayName("refund.completed: the order id, payment id and refunded amount")
    void refundCompleted() throws IOException {
        RefundCompletedEvent event = read("refund.completed", RefundCompletedEvent.class);

        assertThat(event.getOrderId()).isNotNull();
        assertThat(event.getPaymentId()).isNotNull();
        assertThat(event.getRefundAmount()).isNotNull();
    }
}
