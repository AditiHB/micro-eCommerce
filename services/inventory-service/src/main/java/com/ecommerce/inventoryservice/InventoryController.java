package com.ecommerce.inventoryservice;

import com.ecommerce.inventoryservice.dto.CreateInventoryRequest;
import com.ecommerce.inventoryservice.dto.InventoryResponse;
import com.ecommerce.inventoryservice.service.InventoryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/inventory")
@RequiredArgsConstructor
@Slf4j
public class InventoryController {

    private final InventoryService inventoryService;

    /**
     * Get all inventory items.
     */
    @GetMapping
    public ResponseEntity<List<InventoryResponse>> getAll() {
        log.info("GET /api/inventory - Retrieving all inventory items");
        return ResponseEntity.ok(inventoryService.getAllInventory());
    }

    /**
     * Get inventory by ID.
     */
    @GetMapping("/{id}")
    public ResponseEntity<InventoryResponse> getById(@PathVariable Long id) {
        log.info("GET /api/inventory/{} - Retrieving inventory", id);
        return ResponseEntity.ok(inventoryService.getInventory(id));
    }

    /**
     * Create new inventory for a product.
     */
    @PostMapping
    public ResponseEntity<InventoryResponse> createInventory(@Valid @RequestBody CreateInventoryRequest request) {
        log.info("POST /api/inventory - Creating inventory for product: {}", request.getProductId());
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(inventoryService.createInventory(request));
    }

    /**
     * Reserve stock for an order.
     */
    @PostMapping("/{id}/reserve")
    public ResponseEntity<InventoryResponse> reserveStock(
            @PathVariable Long id,
            @RequestParam Integer quantity) {
        log.info("POST /api/inventory/{}/reserve - Reserving {} units", id, quantity);
        return ResponseEntity.ok(inventoryService.reserveStock(id, quantity));
    }

    /**
     * Release reserved stock.
     */
    @PostMapping("/{id}/release")
    public ResponseEntity<InventoryResponse> releaseStock(
            @PathVariable Long id,
            @RequestParam Integer quantity) {
        log.info("POST /api/inventory/{}/release - Releasing {} units", id, quantity);
        return ResponseEntity.ok(inventoryService.releaseStock(id, quantity));
    }

    /**
     * Update inventory quantity.
     */
    @PutMapping("/{id}")
    public ResponseEntity<InventoryResponse> updateInventory(
            @PathVariable Long id,
            @RequestParam Integer quantity) {
        log.info("PUT /api/inventory/{} - Updating quantity to {}", id, quantity);
        return ResponseEntity.ok(inventoryService.updateInventory(id, quantity));
    }
}
