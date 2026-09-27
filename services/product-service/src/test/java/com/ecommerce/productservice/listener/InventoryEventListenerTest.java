package com.ecommerce.productservice.listener;

import com.ecommerce.productservice.event.InventoryReleasedEvent;
import com.ecommerce.productservice.event.InventoryReservedEvent;
import com.ecommerce.productservice.service.ProductService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.doThrow;

@ExtendWith(MockitoExtension.class)
class InventoryEventListenerTest {
    @Mock
    private ProductService productService;

    @InjectMocks
    private InventoryEventListener inventoryEventListener;

    @Test
    void testOnInventoryReservedEvent_Success() {
        InventoryReservedEvent event = InventoryReservedEvent.builder()
                .productId(1L)
                .quantityReserved(10)
                .eventTime(LocalDateTime.now())
                .build();

        inventoryEventListener.onInventoryReservedEvent(event);

        verify(productService).reserveInventory(1L, 10);
    }

    @Test
    void testOnInventoryReservedEvent_HandleException() {
        InventoryReservedEvent event = InventoryReservedEvent.builder()
                .productId(1L)
                .quantityReserved(10)
                .eventTime(LocalDateTime.now())
                .build();

        doThrow(new RuntimeException("Product not found"))
                .when(productService).reserveInventory(1L, 10);

        inventoryEventListener.onInventoryReservedEvent(event);

        verify(productService).reserveInventory(1L, 10);
    }

    @Test
    void testOnInventoryReleasedEvent_Success() {
        InventoryReleasedEvent event = InventoryReleasedEvent.builder()
                .productId(1L)
                .quantityReleased(10)
                .eventTime(LocalDateTime.now())
                .build();

        inventoryEventListener.onInventoryReleasedEvent(event);

        verify(productService).releaseInventory(1L, 10);
    }

    @Test
    void testOnInventoryReleasedEvent_HandleException() {
        InventoryReleasedEvent event = InventoryReleasedEvent.builder()
                .productId(1L)
                .quantityReleased(10)
                .eventTime(LocalDateTime.now())
                .build();

        doThrow(new RuntimeException("Product not found"))
                .when(productService).releaseInventory(1L, 10);

        inventoryEventListener.onInventoryReleasedEvent(event);

        verify(productService).releaseInventory(1L, 10);
    }
}
