package com.ecommerce.inventoryservice;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

@DisplayName("Inventory Entity Unit Tests")
class InventoryEntityTest {

    @Test
    @DisplayName("Should create inventory entity")
    void testInventoryCreation() {
        Inventory inventory = Inventory.builder()
            .id(1L)
            .productId("PROD-001")
            .quantity(100)
            .build();

        assertThat(inventory.getId()).isEqualTo(1L);
        assertThat(inventory.getProductId()).isEqualTo("PROD-001");
        assertThat(inventory.getQuantity()).isEqualTo(100);
    }

    @Test
    @DisplayName("Should update inventory quantity")
    void testUpdateInventoryQuantity() {
        Inventory inventory = Inventory.builder()
            .productId("PROD-001")
            .quantity(100)
            .build();

        inventory.setQuantity(75);

        assertThat(inventory.getQuantity()).isEqualTo(75);
    }

    @Test
    @DisplayName("Should handle zero quantity")
    void testZeroQuantity() {
        Inventory inventory = Inventory.builder()
            .productId("PROD-001")
            .quantity(0)
            .build();

        assertThat(inventory.getQuantity()).isEqualTo(0);
    }
}
