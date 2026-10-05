package com.ecommerce.inventoryservice.service;

import com.ecommerce.common.exception.BusinessException;
import com.ecommerce.common.exception.ConflictException;
import com.ecommerce.common.exception.ResourceNotFoundException;
import com.ecommerce.inventoryservice.Inventory;
import com.ecommerce.inventoryservice.InventoryRepository;
import com.ecommerce.inventoryservice.dto.CreateInventoryRequest;
import com.ecommerce.inventoryservice.dto.InventoryResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** The rules of the stock operations that need no database; their atomicity is in InventoryServiceIntegrationTest. */
@ExtendWith(MockitoExtension.class)
@DisplayName("InventoryService")
class InventoryServiceTest {

    @Mock
    private InventoryRepository repository;

    private InventoryService service;

    @BeforeEach
    void setUp() {
        service = new InventoryService(repository);
    }

    private Inventory stock(int quantity) {
        return Inventory.builder().id(1L).productId("SKU-001").quantity(quantity).version(2L).build();
    }

    @Test
    @DisplayName("reserving takes stock with one conditional statement and returns the new level")
    void reserve() {
        when(repository.decrementByIdIfAvailable(1L, 3)).thenReturn(1);
        when(repository.findById(1L)).thenReturn(Optional.of(stock(7)));

        InventoryResponse response = service.reserveStock(1L, 3);

        assertThat(response.getQuantity()).isEqualTo(7);
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("asking for more than is left is a 409 naming what is available, and nothing is changed")
    void insufficient() {
        when(repository.decrementByIdIfAvailable(1L, 50)).thenReturn(0);
        when(repository.findById(1L)).thenReturn(Optional.of(stock(10)));

        assertThatThrownBy(() -> service.reserveStock(1L, 50))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("Available: 10")
                .extracting(e -> ((ConflictException) e).getErrorCode()).isEqualTo("INSUFFICIENT_STOCK");
    }

    @Test
    @DisplayName("reserving from an item that does not exist is a 404")
    void reserveUnknown() {
        when(repository.decrementByIdIfAvailable(9L, 1)).thenReturn(0);
        when(repository.findById(9L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.reserveStock(9L, 1)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("a zero or negative movement is rejected before touching the database: a negative reserve would ADD stock")
    void nonPositiveMovements() {
        assertThatThrownBy(() -> service.reserveStock(1L, -50)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.reserveStock(1L, 0)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.releaseStock(1L, 0)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.releaseStock(1L, null)).isInstanceOf(BusinessException.class);
        verify(repository, never()).decrementByIdIfAvailable(any(), org.mockito.ArgumentMatchers.anyInt());
        verify(repository, never()).incrementById(any(), org.mockito.ArgumentMatchers.anyInt());
    }

    @Test
    @DisplayName("releasing returns stock; releasing to an unknown item is a 404")
    void release() {
        when(repository.incrementById(1L, 4)).thenReturn(1);
        when(repository.findById(1L)).thenReturn(Optional.of(stock(14)));
        when(repository.incrementById(9L, 4)).thenReturn(0);

        assertThat(service.releaseStock(1L, 4).getQuantity()).isEqualTo(14);
        assertThatThrownBy(() -> service.releaseStock(9L, 4)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("a stock-take can set the level to zero but not below")
    void update() {
        Inventory current = stock(10);
        when(repository.findById(1L)).thenReturn(Optional.of(current));
        when(repository.saveAndFlush(current)).thenReturn(current);

        assertThat(service.updateInventory(1L, 0).getQuantity()).isZero();
        assertThatThrownBy(() -> service.updateInventory(1L, -1)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.updateInventory(1L, null)).isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("creating inventory for a product that already has some is a 409")
    void duplicateProduct() {
        when(repository.findByProductId("SKU-001")).thenReturn(Optional.of(stock(1)));

        assertThatThrownBy(() -> service.createInventory(CreateInventoryRequest.builder().productId("SKU-001").quantity(5).build()))
                .isInstanceOf(ConflictException.class);
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("sorting is limited to known fields")
    void sortAllowList() {
        assertThatThrownBy(() -> service.getAllInventory(0, 20, "version; drop table inventory"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode()).isEqualTo("INVALID_SORT_FIELD");
    }
}
