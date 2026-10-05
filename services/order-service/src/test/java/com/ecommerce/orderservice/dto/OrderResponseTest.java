package com.ecommerce.orderservice.dto;

import com.ecommerce.common.config.JacksonConfig;
import com.ecommerce.common.enums.OrderStatus;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("OrderResponse wire format")
class OrderResponseTest {

    @Test
    @DisplayName("an order is rendered with its priced lines, total, currency and version")
    void rendersTheOrderAggregate() throws Exception {
        OrderResponse response = OrderResponse.builder().id(5L).customerId(7L).status(OrderStatus.PENDING)
                .currency("USD").totalAmount(new BigDecimal("172.97")).version(2L)
                .createdAt(LocalDateTime.parse("2026-01-01T10:00:00"))
                .items(List.of(OrderResponse.Line.builder().productId("SKU-001").quantity(2)
                        .unitPrice(new BigDecimal("79.99")).lineTotal(new BigDecimal("159.98")).build()))
                .build();

        JsonNode json = JacksonConfig.newObjectMapper().valueToTree(response);

        assertThat(json.get("id").asLong()).isEqualTo(5L);
        assertThat(json.get("status").asText()).isEqualTo("PENDING");
        assertThat(json.get("currency").asText()).isEqualTo("USD");
        assertThat(json.get("totalAmount").decimalValue()).isEqualByComparingTo("172.97");
        assertThat(json.get("version").asLong()).isEqualTo(2L);
        assertThat(json.get("items")).hasSize(1);
        assertThat(json.get("items").get(0).get("lineTotal").decimalValue()).isEqualByComparingTo("159.98");
        // the old single-product shape is gone from responses
        assertThat(json.has("productId")).isFalse();
        assertThat(json.has("quantity")).isFalse();
    }
}
