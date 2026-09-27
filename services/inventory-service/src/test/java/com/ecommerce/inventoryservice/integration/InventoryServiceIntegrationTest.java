package com.ecommerce.inventoryservice.integration;

import com.ecommerce.common.exception.BusinessException;
import com.ecommerce.common.exception.ResourceNotFoundException;
import com.ecommerce.inventoryservice.Inventory;
import com.ecommerce.inventoryservice.InventoryRepository;
import com.ecommerce.inventoryservice.dto.CreateInventoryRequest;
import com.ecommerce.inventoryservice.dto.InventoryResponse;
import com.ecommerce.inventoryservice.service.InventoryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.AutoConfigureTestEntityManager;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@AutoConfigureTestEntityManager
@Transactional
@ActiveProfiles("test")
@DisplayName("Inventory Service Integration Tests")
class InventoryServiceIntegrationTest {

    @Autowired
    private InventoryService inventoryService;

    @Autowired
    private InventoryRepository inventoryRepository;

    @BeforeEach
    void setUp() {
        inventoryRepository.deleteAll();
    }

    @Test
    @DisplayName("Should create and retrieve inventory with full context")
    void testCreateAndRetrieveInventory() {
        CreateInventoryRequest request = CreateInventoryRequest.builder()
            .productId("PROD-001")
            .quantity(100)
            .build();

        InventoryResponse created = inventoryService.createInventory(request);
        InventoryResponse retrieved = inventoryService.getInventory(created.getId());

        assertThat(retrieved).isNotNull();
        assertThat(retrieved.getProductId()).isEqualTo("PROD-001");
        assertThat(retrieved.getQuantity()).isEqualTo(100);
    }

    @Test
    @DisplayName("Should reserve stock and persist changes")
    void testReserveStockPersistence() {
        Inventory inventory = Inventory.builder()
            .productId("PROD-001")
            .quantity(100)
            .build();
        Inventory saved = inventoryRepository.save(inventory);

        inventoryService.reserveStock(saved.getId(), 30);

        Inventory verified = inventoryRepository.findById(saved.getId()).orElseThrow();
        assertThat(verified.getQuantity()).isEqualTo(70);
    }

    @Test
    @DisplayName("Should release stock and persist changes")
    void testReleaseStockPersistence() {
        Inventory inventory = Inventory.builder()
            .productId("PROD-001")
            .quantity(50)
            .build();
        Inventory saved = inventoryRepository.save(inventory);

        inventoryService.releaseStock(saved.getId(), 20);

        Inventory verified = inventoryRepository.findById(saved.getId()).orElseThrow();
        assertThat(verified.getQuantity()).isEqualTo(70);
    }

    @Test
    @DisplayName("Should handle consecutive reservations")
    void testConsecutiveReservations() {
        Inventory inventory = Inventory.builder()
            .productId("PROD-001")
            .quantity(100)
            .build();
        Inventory saved = inventoryRepository.save(inventory);

        inventoryService.reserveStock(saved.getId(), 30);
        inventoryService.reserveStock(saved.getId(), 20);
        inventoryService.reserveStock(saved.getId(), 10);

        Inventory verified = inventoryRepository.findById(saved.getId()).orElseThrow();
        assertThat(verified.getQuantity()).isEqualTo(40);
    }

    @Test
    @DisplayName("Should throw exception on insufficient stock")
    void testInsufficientStockThrowsException() {
        Inventory inventory = Inventory.builder()
            .productId("PROD-001")
            .quantity(50)
            .build();
        Inventory saved = inventoryRepository.save(inventory);

        assertThatThrownBy(() -> inventoryService.reserveStock(saved.getId(), 100))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("Insufficient stock");

        Inventory verified = inventoryRepository.findById(saved.getId()).orElseThrow();
        assertThat(verified.getQuantity()).isEqualTo(50);
    }

    @Test
    @DisplayName("Should retrieve all inventory with pagination")
    void testInventoryPaginationWithMultipleItems() {
        for (int i = 1; i <= 15; i++) {
            Inventory inventory = Inventory.builder()
                .productId("PROD-" + i)
                .quantity(i * 10)
                .build();
            inventoryRepository.save(inventory);
        }

        var page1 = inventoryService.getAllInventory(0, 5, "id");
        var page2 = inventoryService.getAllInventory(1, 5, "id");
        var page3 = inventoryService.getAllInventory(2, 5, "id");

        assertThat(page1.getContent()).hasSize(5);
        assertThat(page2.getContent()).hasSize(5);
        assertThat(page3.getContent()).hasSize(5);
        assertThat(page1.getTotalElements()).isEqualTo(15);
    }

    @Test
    @DisplayName("Should update inventory quantity and persist")
    void testUpdateInventoryPersistence() {
        Inventory inventory = Inventory.builder()
            .productId("PROD-001")
            .quantity(100)
            .build();
        Inventory saved = inventoryRepository.save(inventory);

        inventoryService.updateInventory(saved.getId(), 200);

        Inventory verified = inventoryRepository.findById(saved.getId()).orElseThrow();
        assertThat(verified.getQuantity()).isEqualTo(200);
    }

    @Test
    @DisplayName("Should handle reserve and release cycle")
    void testReserveAndReleaseCycle() {
        Inventory inventory = Inventory.builder()
            .productId("PROD-001")
            .quantity(100)
            .build();
        Inventory saved = inventoryRepository.save(inventory);

        inventoryService.reserveStock(saved.getId(), 30);
        Inventory reserved = inventoryRepository.findById(saved.getId()).orElseThrow();
        assertThat(reserved.getQuantity()).isEqualTo(70);

        inventoryService.releaseStock(saved.getId(), 30);
        Inventory released = inventoryRepository.findById(saved.getId()).orElseThrow();
        assertThat(released.getQuantity()).isEqualTo(100);
    }

    @Test
    @DisplayName("Should handle zero quantity reservation edge case")
    void testZeroQuantityReservation() {
        Inventory inventory = Inventory.builder()
            .productId("PROD-001")
            .quantity(100)
            .build();
        Inventory saved = inventoryRepository.save(inventory);

        inventoryService.reserveStock(saved.getId(), 0);

        Inventory verified = inventoryRepository.findById(saved.getId()).orElseThrow();
        assertThat(verified.getQuantity()).isEqualTo(100);
    }

    @Test
    @DisplayName("Should handle exact quantity reservation")
    void testExactQuantityReservation() {
        Inventory inventory = Inventory.builder()
            .productId("PROD-001")
            .quantity(100)
            .build();
        Inventory saved = inventoryRepository.save(inventory);

        inventoryService.reserveStock(saved.getId(), 100);

        Inventory verified = inventoryRepository.findById(saved.getId()).orElseThrow();
        assertThat(verified.getQuantity()).isEqualTo(0);
    }

    @Test
    @DisplayName("Should throw exception when inventory not found on reserve")
    void testReserveOnNonExistentInventory() {
        assertThatThrownBy(() -> inventoryService.reserveStock(999L, 10))
            .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("Should handle timestamps correctly")
    void testTimestampHandling() {
        CreateInventoryRequest request = CreateInventoryRequest.builder()
            .productId("PROD-001")
            .quantity(100)
            .build();

        InventoryResponse response = inventoryService.createInventory(request);

        assertThat(response.getCreatedAt()).isNotNull();
        assertThat(response.getUpdatedAt()).isNotNull();
    }

    @Test
    @DisplayName("Should update timestamp on stock change")
    void testTimestampUpdateOnStockChange() throws InterruptedException {
        Inventory inventory = Inventory.builder()
            .productId("PROD-001")
            .quantity(100)
            .build();
        Inventory saved = inventoryRepository.save(inventory);
        var originalUpdatedAt = saved.getUpdatedAt();

        Thread.sleep(100);

        inventoryService.reserveStock(saved.getId(), 10);

        Inventory updated = inventoryRepository.findById(saved.getId()).orElseThrow();
        assertThat(updated.getUpdatedAt()).isAfter(originalUpdatedAt);
    }

    @Test
    @DisplayName("Should maintain referential integrity on updates")
    void testReferentialIntegrity() {
        Inventory inv1 = Inventory.builder()
            .productId("PROD-001")
            .quantity(100)
            .build();
        Inventory inv2 = Inventory.builder()
            .productId("PROD-002")
            .quantity(50)
            .build();

        Inventory saved1 = inventoryRepository.save(inv1);
        inventoryRepository.save(inv2);

        inventoryService.reserveStock(saved1.getId(), 20);

        long totalInventory = inventoryRepository.count();
        assertThat(totalInventory).isEqualTo(2);
    }
}
