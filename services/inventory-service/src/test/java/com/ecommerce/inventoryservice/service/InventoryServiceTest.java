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

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("InventoryService Unit Tests")
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
            .createdAt(LocalDateTime.now())
            .updatedAt(LocalDateTime.now())
            .build();

        createRequest = CreateInventoryRequest.builder()
            .productId("PROD-001")
            .quantity(100)
            .build();
    }

    @Test
    @DisplayName("Should create inventory successfully")
    void testCreateInventorySuccess() {
        when(inventoryRepository.save(any(Inventory.class))).thenReturn(testInventory);

        InventoryResponse response = inventoryService.createInventory(createRequest);

        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(1L);
        assertThat(response.getProductId()).isEqualTo("PROD-001");
        assertThat(response.getQuantity()).isEqualTo(100);
        verify(inventoryRepository, times(1)).save(any(Inventory.class));
    }

    @Test
    @DisplayName("Should get inventory by ID successfully")
    void testGetInventorySuccess() {
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
    }

    @Test
    @DisplayName("Should get all inventory with pagination")
    void testGetAllInventorySuccess() {
        List<Inventory> inventories = Arrays.asList(testInventory);
        Page<Inventory> page = new PageImpl<>(inventories, mock(Pageable.class), 1);
        when(inventoryRepository.findAll(any(Pageable.class))).thenReturn(page);

        PagedResponse<InventoryResponse> response = inventoryService.getAllInventory(0, 10, "id");

        assertThat(response).isNotNull();
        assertThat(response.getContent()).hasSize(1);
        assertThat(response.getPageNumber()).isEqualTo(0);
    }

    @Test
    @DisplayName("Should reserve stock successfully")
    void testReserveStockSuccess() {
        Inventory updatedInventory = Inventory.builder()
            .id(1L)
            .productId("PROD-001")
            .quantity(75)
            .build();

        when(inventoryRepository.findById(1L)).thenReturn(Optional.of(testInventory));
        when(inventoryRepository.save(any(Inventory.class))).thenReturn(updatedInventory);

        InventoryResponse response = inventoryService.reserveStock(1L, 25);

        assertThat(response.getQuantity()).isEqualTo(75);
        verify(inventoryRepository, times(1)).save(any(Inventory.class));
    }

    @Test
    @DisplayName("Should throw exception when insufficient stock")
    void testReserveStockInsufficientStock() {
        when(inventoryRepository.findById(1L)).thenReturn(Optional.of(testInventory));

        assertThatThrownBy(() -> inventoryService.reserveStock(1L, 150))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("Insufficient stock");
    }

    @Test
    @DisplayName("Should release stock successfully")
    void testReleaseStockSuccess() {
        Inventory updatedInventory = Inventory.builder()
            .id(1L)
            .productId("PROD-001")
            .quantity(125)
            .build();

        when(inventoryRepository.findById(1L)).thenReturn(Optional.of(testInventory));
        when(inventoryRepository.save(any(Inventory.class))).thenReturn(updatedInventory);

        InventoryResponse response = inventoryService.releaseStock(1L, 25);

        assertThat(response.getQuantity()).isEqualTo(125);
        verify(inventoryRepository, times(1)).save(any(Inventory.class));
    }

    @Test
    @DisplayName("Should throw exception when releasing stock from non-existent inventory")
    void testReleaseStockNotFound() {
        when(inventoryRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> inventoryService.releaseStock(999L, 10))
            .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("Should update inventory successfully")
    void testUpdateInventorySuccess() {
        Inventory updatedInventory = Inventory.builder()
            .id(1L)
            .productId("PROD-001")
            .quantity(50)
            .build();

        when(inventoryRepository.findById(1L)).thenReturn(Optional.of(testInventory));
        when(inventoryRepository.save(any(Inventory.class))).thenReturn(updatedInventory);

        InventoryResponse response = inventoryService.updateInventory(1L, 50);

        assertThat(response.getQuantity()).isEqualTo(50);
        verify(inventoryRepository, times(1)).save(any(Inventory.class));
    }

    @Test
    @DisplayName("Should throw exception when updating non-existent inventory")
    void testUpdateInventoryNotFound() {
        when(inventoryRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> inventoryService.updateInventory(999L, 50))
            .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("Should handle zero quantity reserve")
    void testReserveZeroQuantity() {
        when(inventoryRepository.findById(1L)).thenReturn(Optional.of(testInventory));
        when(inventoryRepository.save(any(Inventory.class))).thenReturn(testInventory);

        InventoryResponse response = inventoryService.reserveStock(1L, 0);

        assertThat(response).isNotNull();
    }

    @Test
    @DisplayName("Should handle exact quantity reserve")
    void testReserveExactQuantity() {
        Inventory updatedInventory = Inventory.builder()
            .id(1L)
            .productId("PROD-001")
            .quantity(0)
            .build();

        when(inventoryRepository.findById(1L)).thenReturn(Optional.of(testInventory));
        when(inventoryRepository.save(any(Inventory.class))).thenReturn(updatedInventory);

        InventoryResponse response = inventoryService.reserveStock(1L, 100);

        assertThat(response.getQuantity()).isEqualTo(0);
    }

    @Test
    @DisplayName("Should enforce max page size")
    void testGetAllInventoryMaxPageSize() {
        List<Inventory> inventories = Arrays.asList(testInventory);
        Page<Inventory> page = new PageImpl<>(inventories, mock(Pageable.class), 1);
        when(inventoryRepository.findAll(any(Pageable.class))).thenReturn(page);

        inventoryService.getAllInventory(0, 1000, "id");

        verify(inventoryRepository, times(1)).findAll(argThat(pageable ->
            pageable.getPageSize() <= ApiConstants.MAX_PAGE_SIZE
        ));
    }

    @Test
    @DisplayName("Should handle negative quantity in update")
    void testUpdateInventoryNegativeQuantity() {
        Inventory negativeInventory = Inventory.builder()
            .id(1L)
            .productId("PROD-001")
            .quantity(-10)
            .build();

        when(inventoryRepository.findById(1L)).thenReturn(Optional.of(testInventory));
        when(inventoryRepository.save(any(Inventory.class))).thenReturn(negativeInventory);

        InventoryResponse response = inventoryService.updateInventory(1L, -10);

        assertThat(response.getQuantity()).isEqualTo(-10);
    }
}
