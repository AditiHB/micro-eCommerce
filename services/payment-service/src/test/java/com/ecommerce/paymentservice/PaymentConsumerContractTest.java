package com.ecommerce.paymentservice;

import com.ecommerce.common.config.JacksonConfig;
import com.ecommerce.common.events.InventoryReservedEvent;
import com.ecommerce.common.events.OrderCancelledEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Consumer-driven contract: what payment needs from the events it consumes, checked against the producers'
 * published golden files. Charging the right amount depends on {@code totalAmount} and {@code currency} surviving
 * every hop of the saga - this test is what notices if one of them stops doing so.
 */
@DisplayName("Payment service as a consumer of saga events")
class PaymentConsumerContractTest {

    private static final ObjectMapper MAPPER = JacksonConfig.newObjectMapper();

    private static <T> T read(String type, Class<T> eventClass) throws IOException {
        try (InputStream in = PaymentConsumerContractTest.class.getClassLoader().getResourceAsStream("contracts/" + type + ".v1.json")) {
            assertThat(in).as("golden contract for %s", type).isNotNull();
            return MAPPER.readValue(in, eventClass);
        }
    }

    @Test
    @DisplayName("inventory.reserved: order, customer, a positive total and a currency to charge")
    void inventoryReserved() throws IOException {
        InventoryReservedEvent event = read("inventory.reserved", InventoryReservedEvent.class);

        assertThat(event.getEventId()).isNotBlank();
        assertThat(event.getOrderId()).isNotNull();
        assertThat(event.getCustomerId()).isNotNull();
        assertThat(event.getTotalAmount()).isPositive();
        assertThat(event.getCurrency()).hasSize(3);
    }

    @Test
    @DisplayName("order.cancelled: the order id (to find the payment to refund)")
    void orderCancelled() throws IOException {
        OrderCancelledEvent event = read("order.cancelled", OrderCancelledEvent.class);

        assertThat(event.getEventId()).isNotBlank();
        assertThat(event.getOrderId()).isNotNull();
    }
}
