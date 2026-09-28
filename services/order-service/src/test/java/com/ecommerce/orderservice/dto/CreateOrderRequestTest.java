package com.ecommerce.orderservice.dto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

@DisplayName("CreateOrderRequest DTO Tests")
class CreateOrderRequestTest {

    @Test
    @DisplayName("Should create request with valid data")
    void testValidRequest() {
        CreateOrderRequest request = CreateOrderRequest.builder()
            .customerId(123L)
            .productId("PROD-001")
            .quantity(5)
            .build();

        assertThat(request.getCustomerId()).isEqualTo(123L);
        assertThat(request.getProductId()).isEqualTo("PROD-001");
        assertThat(request.getQuantity()).isEqualTo(5);
    }

    @Test
    @DisplayName("Should update customer ID via setter")
    void testSetCustomerId() {
        CreateOrderRequest request = new CreateOrderRequest();
        request.setCustomerId(456L);

        assertThat(request.getCustomerId()).isEqualTo(456L);
    }

    @Test
    @DisplayName("Should update product ID via setter")
    void testSetProductId() {
        CreateOrderRequest request = new CreateOrderRequest();
        request.setProductId("PROD-002");

        assertThat(request.getProductId()).isEqualTo("PROD-002");
    }

    @Test
    @DisplayName("Should update quantity via setter")
    void testSetQuantity() {
        CreateOrderRequest request = new CreateOrderRequest();
        request.setQuantity(10);

        assertThat(request.getQuantity()).isEqualTo(10);
    }

    @Test
    @DisplayName("Should support all-args constructor")
    void testAllArgsConstructor() {
        CreateOrderRequest request = new CreateOrderRequest(789L, "PROD-003", 15);

        assertThat(request.getCustomerId()).isEqualTo(789L);
        assertThat(request.getProductId()).isEqualTo("PROD-003");
        assertThat(request.getQuantity()).isEqualTo(15);
    }

    @Test
    @DisplayName("Should support no-args constructor")
    void testNoArgsConstructor() {
        CreateOrderRequest request = new CreateOrderRequest();

        assertThat(request.getCustomerId()).isNull();
        assertThat(request.getProductId()).isNull();
        assertThat(request.getQuantity()).isNull();
    }

    @Test
    @DisplayName("Should handle single quantity")
    void testSingleQuantity() {
        CreateOrderRequest request = CreateOrderRequest.builder()
            .customerId(999L)
            .productId("PROD-999")
            .quantity(1)
            .build();

        assertThat(request.getQuantity()).isEqualTo(1);
    }
}
