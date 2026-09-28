package com.ecommerce.inventoryservice.service;

import com.ecommerce.common.constants.ApiConstants;
import com.ecommerce.common.dto.PagedResponse;
import com.ecommerce.common.exception.BusinessException;
import com.ecommerce.common.exception.ResourceNotFoundException;
import com.ecommerce.inventoryservice.Inventory;
import com.ecommerce.inventoryservice.InventoryRepository;
import com.ecommerce.inventoryservice.dto.CreateInventoryRequest;
import com.ecommerce.inventoryservice.dto.InventoryResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Inventory Service Unit Tests")
class InventoryServiceTest {

    @Mock
    private InventoryRepository inventoryRepository;

    @InjectMocks
    private InventoryService inventoryService;

    private Inventory testInventory;
    private CreateInventoryRequest createRequest;

    @BeforeEach
    void setUp() {
        testInventory = Inventory.builder()
            .id(1L)
            .productId("PROD-001")
            .quantity(100)
            .build();

        createRequest = CreateInventoryRequest.builder()
            .productId("PROD-001")
            .quantity(100)
            .build();
    }

    @Test
    @DisplayName("Should create inventory successfully")
    void testCreateInventory() {
        when(inventoryRepository.save(any(Inventory.class))).thenReturn(testInventory);

        InventoryResponse response = inventoryService.createInventory(createRequest);

        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(1L);
        assertThat(response.getProductId()).isEqualTo("PROD-001");
        assertThat(response.getQuantity()).isEqualTo(100);
        verify(inventoryRepository, times(1)).save(any(Inventory.class));
    }

    @Test
    @DisplayName("Should retrieve inventory by ID successfully")
    void testGetInventory() {
        when(inventoryRepository.findById(1L)).thenReturn(Optional.of(testInventory));

        InventoryResponse response = inventoryService.getInventory(1L);

        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(1L);
        assertThat(response.getProductId()).isEqualTo("PROD-001");
        verify(inventoryRepository, times(1)).findById(1L);
    }

    @Test
    @DisplayName("Should throw exception when inventory not found")
    void testGetInventoryNotFound() {
        when(inventoryRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> inventoryService.getInventory(999L))
            .isInstanceOf(ResourceNotFoundException.class);

        verify(inventoryRepository, times(1)).findById(999L);
    }

    @Test
    @DisplayName("Should retrieve all inventory items with pagination")
    void testGetAllInventory() {
        Inventory inventory2 = Inventory.builder()
            .id(2L)
            .productId("PROD-002")
            .quantity(50)
            .build();

        List<Inventory> inventories = List.of(testInventory, inventory2);
        Page<Inventory> page = new PageImpl<>(inventories);

        when(inventoryRepository.findAll(any(Pageable.class))).thenReturn(page);

        PagedResponse<InventoryResponse> response = inventoryService.getAllInventory(0, 10, "id");

        assertThat(response).isNotNull();
        assertThat(response.getContent()).hasSize(2);
        assertThat(response.getPageNumber()).isEqualTo(0);
        assertThat(response.getPageSize()).isEqualTo(10);
        verify(inventoryRepository, times(1)).findAll(any(Pageable.class));
    }

    @Test
    @DisplayName("Should reserve stock successfully")
    void testReserveStockSuccess() {
        Inventory reservedInventory = Inventory.builder()
            .id(1L)
            .productId("PROD-001")
            .quantity(85)
            .build();

        when(inventoryRepository.findById(1L)).thenReturn(Optional.of(testInventory));
        when(inventoryRepository.save(any(Inventory.class))).thenReturn(reservedInventory);

        InventoryResponse response = inventoryService.reserveStock(1L, 15);

        assertThat(response.getQuantity()).isEqualTo(85);
        verify(inventoryRepository, times(1)).findById(1L);
        verify(inventoryRepository, times(1)).save(any(Inventory.class));
    }

    @Test
    @DisplayName("Should throw exception when insufficient stock")
    void testReserveStockInsufficientStock() {
        when(inventoryRepository.findById(1L)).thenReturn(Optional.of(testInventory));

        assertThatThrownBy(() -> inventoryService.reserveStock(1L, 150))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("Insufficient stock");

        verify(inventoryRepository, times(1)).findById(1L);
        verify(inventoryRepository, never()).save(any(Inventory.class));
    }

    @Test
    @DisplayName("Should throw exception when reserving stock for non-existent inventory")
    void testReserveStockNotFound() {
        when(inventoryRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> inventoryService.reserveStock(999L, 10))
            .isInstanceOf(ResourceNotFoundException.class);

        verify(inventoryRepository, times(1)).findById(999L);
    }

    @Test
    @DisplayName("Should release stock successfully")
    void testReleaseStockSuccess() {
        Inventory releasedInventory = Inventory.builder()
            .id(1L)
            .productId("PROD-001")
            .quantity(115)
            .build();

        when(inventoryRepository.findById(1L)).thenReturn(Optional.of(testInventory));
        when(inventoryRepository.save(any(Inventory.class))).thenReturn(releasedInventory);

        InventoryResponse response = inventoryService.releaseStock(1L, 15);

        assertThat(response.getQuantity()).isEqualTo(115);
        verify(inventoryRepository, times(1)).findById(1L);
        verify(inventoryRepository, times(1)).save(any(Inventory.class));
    }

    @Test
    @DisplayName("Should throw exception when releasing stock for non-existent inventory")
    void testReleaseStockNotFound() {
        when(inventoryRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> inventoryService.releaseStock(999L, 10))
            .isInstanceOf(ResourceNotFoundException.class);

        verify(inventoryRepository, times(1)).findById(999L);
    }

    @Test
    @DisplayName("Should update inventory successfully")
    void testUpdateInventory() {
        Inventory updatedInventory = Inventory.builder()
            .id(1L)
            .productId("PROD-001")
            .quantity(200)
            .build();

        when(inventoryRepository.findById(1L)).thenReturn(Optional.of(testInventory));
        when(inventoryRepository.save(any(Inventory.class))).thenReturn(updatedInventory);

        InventoryResponse response = inventoryService.updateInventory(1L, 200);

        assertThat(response.getQuantity()).isEqualTo(200);
        verify(inventoryRepository, times(1)).findById(1L);
        verify(inventoryRepository, times(1)).save(any(Inventory.class));
    }

    @Test
    @DisplayName("Should throw exception when updating non-existent inventory")
    void testUpdateInventoryNotFound() {
        when(inventoryRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> inventoryService.updateInventory(999L, 200))
            .isInstanceOf(ResourceNotFoundException.class);

        verify(inventoryRepository, times(1)).findById(999L);
    }

    @Test
    @DisplayName("Should handle edge case: reserve exactly available quantity")
    void testReserveExactQuantity() {
        Inventory emptyInventory = Inventory.builder()
            .id(1L)
            .productId("PROD-001")
            .quantity(0)
            .build();

        when(inventoryRepository.findById(1L)).thenReturn(Optional.of(testInventory));
        when(inventoryRepository.save(any(Inventory.class))).thenReturn(emptyInventory);

        InventoryResponse response = inventoryService.reserveStock(1L, 100);

        assertThat(response.getQuantity()).isEqualTo(0);
    }

    @Test
    @DisplayName("Should handle edge case: reserve zero quantity")
    void testReserveZeroQuantity() {
        Inventory zeroReserve = Inventory.builder()
            .id(1L)
            .productId("PROD-001")
            .quantity(100)
            .build();

        when(inventoryRepository.findById(1L)).thenReturn(Optional.of(testInventory));
        when(inventoryRepository.save(any(Inventory.class))).thenReturn(zeroReserve);

        InventoryResponse response = inventoryService.reserveStock(1L, 0);

        assertThat(response.getQuantity()).isEqualTo(100);
    }
}
