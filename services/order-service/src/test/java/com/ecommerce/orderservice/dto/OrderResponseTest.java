package com.ecommerce.orderservice.dto;

import com.ecommerce.common.enums.OrderStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.*;

@DisplayName("OrderResponse DTO Tests")
class OrderResponseTest {

    @Test
    @DisplayName("Should create response with valid data")
    void testValidResponse() {
        OrderResponse response = OrderResponse.builder()
            .id(1L)
            .customerId(123L)
            .productId("PROD-001")
            .quantity(5)
            .status(OrderStatus.PENDING)
            .build();

        assertThat(response.getId()).isEqualTo(1L);
        assertThat(response.getCustomerId()).isEqualTo(123L);
        assertThat(response.getProductId()).isEqualTo("PROD-001");
        assertThat(response.getQuantity()).isEqualTo(5);
        assertThat(response.getStatus()).isEqualTo(OrderStatus.PENDING);
    }

    @Test
    @DisplayName("Should update customer ID via setter")
    void testSetCustomerId() {
        OrderResponse response = new OrderResponse();
        response.setCustomerId(456L);

        assertThat(response.getCustomerId()).isEqualTo(456L);
    }

    @Test
    @DisplayName("Should update product ID via setter")
    void testSetProductId() {
        OrderResponse response = new OrderResponse();
        response.setProductId("PROD-002");

        assertThat(response.getProductId()).isEqualTo("PROD-002");
    }

    @Test
    @DisplayName("Should update quantity via setter")
    void testSetQuantity() {
        OrderResponse response = new OrderResponse();
        response.setQuantity(10);

        assertThat(response.getQuantity()).isEqualTo(10);
    }

    @Test
    @DisplayName("Should update status via setter")
    void testSetStatus() {
        OrderResponse response = new OrderResponse();
        response.setStatus(OrderStatus.COMPLETED);

        assertThat(response.getStatus()).isEqualTo(OrderStatus.COMPLETED);
    }

    @Test
    @DisplayName("Should update ID via setter")
    void testSetId() {
        OrderResponse response = new OrderResponse();
        response.setId(5L);

        assertThat(response.getId()).isEqualTo(5L);
    }

    @Test
    @DisplayName("Should support all-args constructor")
    void testAllArgsConstructor() {
        LocalDateTime now = LocalDateTime.now();
        OrderResponse response = new OrderResponse(10L, 789L, "PROD-003", 15, OrderStatus.COMPLETED, now, now);

        assertThat(response.getId()).isEqualTo(10L);
        assertThat(response.getCustomerId()).isEqualTo(789L);
        assertThat(response.getProductId()).isEqualTo("PROD-003");
        assertThat(response.getQuantity()).isEqualTo(15);
        assertThat(response.getStatus()).isEqualTo(OrderStatus.COMPLETED);
        assertThat(response.getCreatedAt()).isEqualTo(now);
        assertThat(response.getUpdatedAt()).isEqualTo(now);
    }

    @Test
    @DisplayName("Should support no-args constructor")
    void testNoArgsConstructor() {
        OrderResponse response = new OrderResponse();

        assertThat(response.getId()).isNull();
        assertThat(response.getCustomerId()).isNull();
        assertThat(response.getProductId()).isNull();
        assertThat(response.getQuantity()).isNull();
        assertThat(response.getStatus()).isNull();
    }
}
