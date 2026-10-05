package com.ecommerce.inventoryservice;

import com.ecommerce.common.config.JacksonConfig;
import com.ecommerce.common.events.OrderCancelledEvent;
import com.ecommerce.common.events.OrderCreatedEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Consumer-driven contract: what inventory needs from the order events it consumes, checked against the
 * producer's published golden files. If the order service renames or drops one of these fields, this test - not
 * production - finds out.
 */
@DisplayName("Inventory service as a consumer of order events")
class InventoryConsumerContractTest {

    private static final ObjectMapper MAPPER = JacksonConfig.newObjectMapper();

    private static <T> T read(String type, Class<T> eventClass) throws IOException {
        try (InputStream in = InventoryConsumerContractTest.class.getClassLoader().getResourceAsStream("contracts/" + type + ".v1.json")) {
            assertThat(in).as("golden contract for %s", type).isNotNull();
            return MAPPER.readValue(in, eventClass);
        }
    }

    @Test
    @DisplayName("order.created: order, customer, priced lines, total and currency - everything it passes on")
    void orderCreated() throws IOException {
        OrderCreatedEvent event = read("order.created", OrderCreatedEvent.class);

        assertThat(event.getEventId()).isNotBlank();
        assertThat(event.getOrderId()).isNotNull();
        assertThat(event.getCustomerId()).isNotNull();
        assertThat(event.getLines()).isNotEmpty().allSatisfy(l -> {
            assertThat(l.getProductId()).isNotBlank();
            assertThat(l.getQuantity()).isPositive();
        });
        assertThat(event.getTotalAmount()).isNotNull();
        assertThat(event.getCurrency()).isNotBlank();
    }

    @Test
    @DisplayName("order.cancelled: just the order id (the reservation itself says what to release)")
    void orderCancelled() throws IOException {
        OrderCancelledEvent event = read("order.cancelled", OrderCancelledEvent.class);

        assertThat(event.getEventId()).isNotBlank();
        assertThat(event.getOrderId()).isNotNull();
    }
}
