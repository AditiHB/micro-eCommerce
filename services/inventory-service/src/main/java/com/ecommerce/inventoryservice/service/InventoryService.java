package com.ecommerce.inventoryservice.service;

import com.ecommerce.common.constants.ApiConstants;
import com.ecommerce.common.dto.PagedResponse;
import com.ecommerce.common.exception.BusinessException;
import com.ecommerce.common.exception.ConflictException;
import com.ecommerce.common.exception.ResourceNotFoundException;
import com.ecommerce.inventoryservice.Inventory;
import com.ecommerce.inventoryservice.InventoryRepository;
import com.ecommerce.inventoryservice.dto.CreateInventoryRequest;
import com.ecommerce.inventoryservice.dto.InventoryResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

/**
 * Back-office stock operations. Stock levels are workflow state - they change on every order - so they are never
 * cached. Taking and returning stock are single conditional statements (see {@link InventoryRepository}), not
 * read-modify-write: two concurrent callers can never both take the last unit or lose each other's update.
 * The saga's own stock handling, per order, is in {@link ReservationService}.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class InventoryService {

    static final Set<String> SORTABLE_FIELDS = Set.of("id", "productId", "quantity", "createdAt", "updatedAt");

    private final InventoryRepository inventoryRepository;

    public InventoryResponse createInventory(CreateInventoryRequest request) {
        log.info("Creating inventory for product: {}", request.getProductId());
        if (inventoryRepository.findByProductId(request.getProductId()).isPresent()) {
            throw new ConflictException("Inventory already exists for product " + request.getProductId(), "INVENTORY_ALREADY_EXISTS");
        }
        Inventory saved = inventoryRepository.saveAndFlush(Inventory.builder()
                .productId(request.getProductId())
                .quantity(request.getQuantity())
                .build());
        return mapToResponse(saved);
    }

    @Transactional(readOnly = true)
    public InventoryResponse getInventory(Long id) {
        return mapToResponse(find(id));
    }

    @Transactional(readOnly = true)
    public PagedResponse<InventoryResponse> getAllInventory(int pageNumber, int pageSize, String sortBy) {
        if (!SORTABLE_FIELDS.contains(sortBy)) {
            throw new BusinessException("Cannot sort by '" + sortBy + "'. Allowed: " + SORTABLE_FIELDS, "INVALID_SORT_FIELD");
        }
        int size = Math.min(Math.max(pageSize, 1), ApiConstants.MAX_PAGE_SIZE);
        Pageable pageable = PageRequest.of(Math.max(pageNumber, 0), size, Sort.by(sortBy).ascending());
        Page<Inventory> page = inventoryRepository.findAll(pageable);
        List<InventoryResponse> responses = page.getContent().stream().map(this::mapToResponse).toList();
        return PagedResponse.of(responses, pageNumber, size, page.getTotalElements());
    }

    /**
     * Takes stock. Exactly what is left can be taken (down to zero); more than is left is a 409 and changes nothing.
     */
    public InventoryResponse reserveStock(Long id, Integer quantity) {
        requirePositive(quantity);
        if (inventoryRepository.decrementByIdIfAvailable(id, quantity) == 0) {
            Inventory inventory = find(id);
            throw new ConflictException(
                    "Insufficient stock. Available: " + inventory.getQuantity() + ", Requested: " + quantity, "INSUFFICIENT_STOCK");
        }
        log.info("Took {} units from inventory {}", quantity, id);
        return mapToResponse(find(id));
    }

    /** Returns stock. */
    public InventoryResponse releaseStock(Long id, Integer quantity) {
        requirePositive(quantity);
        if (inventoryRepository.incrementById(id, quantity) == 0) {
            throw new ResourceNotFoundException("Inventory", id);
        }
        log.info("Returned {} units to inventory {}", quantity, id);
        return mapToResponse(find(id));
    }

    /** Sets the stock level to an absolute value (a stock-take). Zero is a valid level. */
    public InventoryResponse updateInventory(Long id, Integer newQuantity) {
        if (newQuantity == null || newQuantity < 0) {
            throw new BusinessException("Quantity cannot be negative", "INVALID_QUANTITY");
        }
        Inventory inventory = find(id);
        inventory.setQuantity(newQuantity);
        return mapToResponse(inventoryRepository.saveAndFlush(inventory));
    }

    private Inventory find(Long id) {
        return inventoryRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Inventory", id));
    }

    /** A movement of stock is a positive number of units; a negative "reservation" would otherwise ADD stock. */
    private static void requirePositive(Integer quantity) {
        if (quantity == null || quantity <= 0) {
            throw new BusinessException("Quantity must be positive", "INVALID_QUANTITY");
        }
    }

    InventoryResponse mapToResponse(Inventory inventory) {
        return InventoryResponse.builder()
                .id(inventory.getId())
                .productId(inventory.getProductId())
                .quantity(inventory.getQuantity())
                .version(inventory.getVersion())
                .createdAt(inventory.getCreatedAt())
                .updatedAt(inventory.getUpdatedAt())
                .build();
    }
}
