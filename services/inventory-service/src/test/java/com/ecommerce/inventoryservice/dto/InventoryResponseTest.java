package com.ecommerce.inventoryservice.dto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

@DisplayName("InventoryResponse DTO Tests")
class InventoryResponseTest {

    @Test
    @DisplayName("Should create response with valid data")
    void testValidResponse() {
        InventoryResponse response = InventoryResponse.builder()
            .id(1L)
            .productId("PROD-001")
            .quantity(100)
            .build();

        assertThat(response.getId()).isEqualTo(1L);
        assertThat(response.getProductId()).isEqualTo("PROD-001");
        assertThat(response.getQuantity()).isEqualTo(100);
    }

    @Test
    @DisplayName("Should update product ID via setter")
    void testSetProductId() {
        InventoryResponse response = new InventoryResponse();
        response.setProductId("PROD-002");

        assertThat(response.getProductId()).isEqualTo("PROD-002");
    }

    @Test
    @DisplayName("Should update quantity via setter")
    void testSetQuantity() {
        InventoryResponse response = new InventoryResponse();
        response.setQuantity(50);

        assertThat(response.getQuantity()).isEqualTo(50);
    }

    @Test
    @DisplayName("Should update ID via setter")
    void testSetId() {
        InventoryResponse response = new InventoryResponse();
        response.setId(5L);

        assertThat(response.getId()).isEqualTo(5L);
    }

    @Test
    @DisplayName("Should support all-args constructor")
    void testAllArgsConstructor() {
        InventoryResponse response = new InventoryResponse(10L, "PROD-003", 200, null, null);

        assertThat(response.getId()).isEqualTo(10L);
        assertThat(response.getProductId()).isEqualTo("PROD-003");
        assertThat(response.getQuantity()).isEqualTo(200);
    }

    @Test
    @DisplayName("Should support no-args constructor")
    void testNoArgsConstructor() {
        InventoryResponse response = new InventoryResponse();

        assertThat(response.getId()).isNull();
        assertThat(response.getProductId()).isNull();
        assertThat(response.getQuantity()).isNull();
    }

    @Test
    @DisplayName("Should handle zero quantity")
    void testZeroQuantity() {
        InventoryResponse response = InventoryResponse.builder()
            .id(1L)
            .productId("PROD-004")
            .quantity(0)
            .build();

        assertThat(response.getQuantity()).isEqualTo(0);
    }
}
