package com.ecommerce.inventoryservice.service;

import com.ecommerce.common.exception.BusinessException;
import com.ecommerce.common.exception.ResourceNotFoundException;
import com.ecommerce.inventoryservice.Inventory;
import com.ecommerce.inventoryservice.InventoryRepository;
import com.ecommerce.inventoryservice.dto.CreateInventoryRequest;
import com.ecommerce.inventoryservice.dto.InventoryResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class InventoryService {

    private final InventoryRepository inventoryRepository;

    /**
     * Creates new inventory for a product.
     *
     * @param request the inventory creation request
     * @return the created inventory response
     */
    public InventoryResponse createInventory(CreateInventoryRequest request) {
        log.info("Creating inventory for product: {}", request.getProductId());

        Inventory inventory = Inventory.builder()
            .productId(request.getProductId())
            .quantity(request.getQuantity())
            .build();

        Inventory savedInventory = inventoryRepository.save(inventory);
        log.info("Inventory created successfully with ID: {}", savedInventory.getId());

        return mapToResponse(savedInventory);
    }

    /**
     * Retrieves inventory by ID.
     *
     * @param id the inventory ID
     * @return the inventory response
     * @throws ResourceNotFoundException if inventory not found
     */
    @Transactional(readOnly = true)
    public InventoryResponse getInventory(Long id) {
        log.info("Fetching inventory with ID: {}", id);

        Inventory inventory = inventoryRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Inventory", id));

        return mapToResponse(inventory);
    }

    /**
     * Retrieves all inventory items.
     *
     * @return list of inventory responses
     */
    @Transactional(readOnly = true)
    public List<InventoryResponse> getAllInventory() {
        log.info("Fetching all inventory items");
        return inventoryRepository.findAll()
            .stream()
            .map(this::mapToResponse)
            .toList();
    }

    /**
     * Reserves stock for an order.
     *
     * @param id the inventory ID
     * @param quantity the quantity to reserve
     * @return the updated inventory response
     * @throws ResourceNotFoundException if inventory not found
     * @throws BusinessException if insufficient stock
     */
    public InventoryResponse reserveStock(Long id, Integer quantity) {
        log.info("Reserving {} units from inventory {}", quantity, id);

        Inventory inventory = inventoryRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Inventory", id));

        if (inventory.getQuantity() < quantity) {
            log.warn("Insufficient stock for product: {}", inventory.getProductId());
            throw new BusinessException(
                "Insufficient stock. Available: " + inventory.getQuantity() + ", Requested: " + quantity,
                "INSUFFICIENT_STOCK"
            );
        }

        inventory.setQuantity(inventory.getQuantity() - quantity);
        Inventory updatedInventory = inventoryRepository.save(inventory);

        log.info("Stock reserved successfully. Remaining quantity: {}", updatedInventory.getQuantity());
        return mapToResponse(updatedInventory);
    }

    /**
     * Releases reserved stock (reversal of reservation).
     *
     * @param id the inventory ID
     * @param quantity the quantity to release
     * @return the updated inventory response
     * @throws ResourceNotFoundException if inventory not found
     */
    public InventoryResponse releaseStock(Long id, Integer quantity) {
        log.info("Releasing {} units to inventory {}", quantity, id);

        Inventory inventory = inventoryRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Inventory", id));

        inventory.setQuantity(inventory.getQuantity() + quantity);
        Inventory updatedInventory = inventoryRepository.save(inventory);

        log.info("Stock released successfully. Current quantity: {}", updatedInventory.getQuantity());
        return mapToResponse(updatedInventory);
    }

    /**
     * Updates inventory quantity.
     *
     * @param id the inventory ID
     * @param newQuantity the new quantity
     * @return the updated inventory response
     * @throws ResourceNotFoundException if inventory not found
     */
    public InventoryResponse updateInventory(Long id, Integer newQuantity) {
        log.info("Updating inventory {} to quantity {}", id, newQuantity);

        Inventory inventory = inventoryRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Inventory", id));

        inventory.setQuantity(newQuantity);
        Inventory updatedInventory = inventoryRepository.save(inventory);

        log.info("Inventory updated successfully");
        return mapToResponse(updatedInventory);
    }

    private InventoryResponse mapToResponse(Inventory inventory) {
        return InventoryResponse.builder()
            .id(inventory.getId())
            .productId(inventory.getProductId())
            .quantity(inventory.getQuantity())
            .createdAt(inventory.getCreatedAt())
            .updatedAt(inventory.getUpdatedAt())
            .build();
    }
}
