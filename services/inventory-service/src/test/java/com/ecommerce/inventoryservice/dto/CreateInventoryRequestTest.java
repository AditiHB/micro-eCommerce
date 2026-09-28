package com.ecommerce.inventoryservice.dto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

@DisplayName("CreateInventoryRequest DTO Tests")
class CreateInventoryRequestTest {

    @Test
    @DisplayName("Should create request with valid data")
    void testValidRequest() {
        CreateInventoryRequest request = CreateInventoryRequest.builder()
            .productId("PROD-001")
            .quantity(100)
            .build();

        assertThat(request.getProductId()).isEqualTo("PROD-001");
        assertThat(request.getQuantity()).isEqualTo(100);
    }

    @Test
    @DisplayName("Should update product ID via setter")
    void testSetProductId() {
        CreateInventoryRequest request = new CreateInventoryRequest();
        request.setProductId("PROD-002");

        assertThat(request.getProductId()).isEqualTo("PROD-002");
    }

    @Test
    @DisplayName("Should update quantity via setter")
    void testSetQuantity() {
        CreateInventoryRequest request = new CreateInventoryRequest();
        request.setQuantity(50);

        assertThat(request.getQuantity()).isEqualTo(50);
    }

    @Test
    @DisplayName("Should support all-args constructor")
    void testAllArgsConstructor() {
        CreateInventoryRequest request = new CreateInventoryRequest("PROD-003", 200);

        assertThat(request.getProductId()).isEqualTo("PROD-003");
        assertThat(request.getQuantity()).isEqualTo(200);
    }

    @Test
    @DisplayName("Should support no-args constructor")
    void testNoArgsConstructor() {
        CreateInventoryRequest request = new CreateInventoryRequest();

        assertThat(request.getProductId()).isNull();
        assertThat(request.getQuantity()).isEqualTo(0);
    }

    @Test
    @DisplayName("Should handle zero quantity")
    void testZeroQuantity() {
        CreateInventoryRequest request = CreateInventoryRequest.builder()
            .productId("PROD-004")
            .quantity(0)
            .build();

        assertThat(request.getQuantity()).isEqualTo(0);
    }

    @Test
    @DisplayName("Should handle large quantity")
    void testLargeQuantity() {
        CreateInventoryRequest request = CreateInventoryRequest.builder()
            .productId("PROD-005")
            .quantity(999999)
            .build();

        assertThat(request.getQuantity()).isEqualTo(999999);
    }
}
