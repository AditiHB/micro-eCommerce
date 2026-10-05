package com.ecommerce.inventoryservice;

import com.ecommerce.common.constants.ApiConstants;
import com.ecommerce.common.dto.PagedResponse;
import com.ecommerce.inventoryservice.dto.CreateInventoryRequest;
import com.ecommerce.inventoryservice.dto.InventoryResponse;
import com.ecommerce.inventoryservice.service.InventoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import com.ecommerce.common.web.EntityTags;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@Validated
@RequestMapping(ApiConstants.API_PREFIX + ApiConstants.INVENTORY_ENDPOINT)
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Inventory Management", description = "APIs for managing inventory")
public class InventoryController {

    private final InventoryService inventoryService;

    /**
     * Get all inventory items with pagination.
     */
    @GetMapping
    @Operation(summary = "Get all inventory items", description = "Retrieve all inventory items with pagination support")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Successfully retrieved inventory items",
            content = @Content(schema = @Schema(implementation = PagedResponse.class))),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<PagedResponse<InventoryResponse>> getAll(
            @Parameter(description = "Page number (0-indexed)", example = "0")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size", example = "20")
            @RequestParam(defaultValue = ApiConstants.DEFAULT_PAGE_SIZE + "") int size,
            @Parameter(description = "Field to sort by", example = "id")
            @RequestParam(defaultValue = "id") String sortBy) {
        log.info("GET /inventory - Retrieving inventory items - page: {}, size: {}, sortBy: {}", page, size, sortBy);
        return ResponseEntity.ok(inventoryService.getAllInventory(page, size, sortBy));
    }

    /**
     * Get inventory by ID.
     */
    @GetMapping("/{id}")
    @Operation(summary = "Get inventory by ID", description = "Retrieve a specific inventory item by their ID")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Inventory found",
            content = @Content(schema = @Schema(implementation = InventoryResponse.class))),
        @ApiResponse(responseCode = "404", description = "Inventory not found"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<InventoryResponse> getById(
            @Parameter(description = "Inventory ID", example = "1")
            @PathVariable Long id) {
        log.info("GET /inventory/{} - Retrieving inventory", id);
        return withEtag(ResponseEntity.ok(), inventoryService.getInventory(id));
    }

    /**
     * Create new inventory for a product.
     */
    @PostMapping
    @Operation(summary = "Create inventory", description = "Create new inventory for a product")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "201", description = "Inventory created successfully",
            content = @Content(schema = @Schema(implementation = InventoryResponse.class))),
        @ApiResponse(responseCode = "400", description = "Invalid input"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<InventoryResponse> createInventory(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Inventory creation request", required = true)
            @Valid @RequestBody CreateInventoryRequest request) {
        log.info("POST /inventory - Creating inventory for product: {}", request.getProductId());
        InventoryResponse created = inventoryService.createInventory(request);
        return withEtag(ResponseEntity.status(HttpStatus.CREATED), created);
    }

    /**
     * Reserve stock for an order.
     */
    @PostMapping("/{id}/reserve")
    @Operation(summary = "Reserve stock", description = "Reserve stock for an order")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Stock reserved successfully",
            content = @Content(schema = @Schema(implementation = InventoryResponse.class))),
        @ApiResponse(responseCode = "400", description = "Insufficient stock"),
        @ApiResponse(responseCode = "404", description = "Inventory not found"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<InventoryResponse> reserveStock(
            @Parameter(description = "Inventory ID", example = "1")
            @PathVariable Long id,
            @Parameter(description = "Quantity to reserve", example = "5")
            @RequestParam @Positive(message = "Quantity must be positive") Integer quantity,
            @RequestHeader(name = HttpHeaders.IF_MATCH, required = false) String ifMatch) {
        log.info("POST /inventory/{}/reserve - Reserving {} units", id, quantity);
        checkIfMatch(id, ifMatch);
        return withEtag(ResponseEntity.ok(), inventoryService.reserveStock(id, quantity));
    }

    /**
     * Release reserved stock.
     */
    @PostMapping("/{id}/release")
    @Operation(summary = "Release stock", description = "Release reserved stock back to inventory")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Stock released successfully",
            content = @Content(schema = @Schema(implementation = InventoryResponse.class))),
        @ApiResponse(responseCode = "404", description = "Inventory not found"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<InventoryResponse> releaseStock(
            @Parameter(description = "Inventory ID", example = "1")
            @PathVariable Long id,
            @Parameter(description = "Quantity to release", example = "5")
            @RequestParam @Positive(message = "Quantity must be positive") Integer quantity,
            @RequestHeader(name = HttpHeaders.IF_MATCH, required = false) String ifMatch) {
        log.info("POST /inventory/{}/release - Releasing {} units", id, quantity);
        checkIfMatch(id, ifMatch);
        return withEtag(ResponseEntity.ok(), inventoryService.releaseStock(id, quantity));
    }

    /**
     * Update inventory quantity.
     */
    @PutMapping("/{id}")
    @Operation(summary = "Update inventory quantity", description = "Update the quantity of an inventory item")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Inventory updated successfully",
            content = @Content(schema = @Schema(implementation = InventoryResponse.class))),
        @ApiResponse(responseCode = "400", description = "Invalid quantity"),
        @ApiResponse(responseCode = "404", description = "Inventory not found"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<InventoryResponse> updateInventory(
            @Parameter(description = "Inventory ID", example = "1")
            @PathVariable Long id,
            @Parameter(description = "New quantity (zero is a valid stock level)", example = "100")
            @RequestParam @PositiveOrZero(message = "Quantity cannot be negative") Integer quantity,
            @RequestHeader(name = HttpHeaders.IF_MATCH, required = false) String ifMatch) {
        log.info("PUT /inventory/{} - Updating quantity to {}", id, quantity);
        checkIfMatch(id, ifMatch);
        return withEtag(ResponseEntity.ok(), inventoryService.updateInventory(id, quantity));
    }

    /** Optimistic concurrency: if the caller names the version they read, refuse the change when it has moved on. */
    private void checkIfMatch(Long id, String ifMatch) {
        if (ifMatch != null && !ifMatch.isBlank()) {
            EntityTags.verifyIfMatch(ifMatch, inventoryService.getInventory(id).getVersion());
        }
    }

    private static ResponseEntity<InventoryResponse> withEtag(ResponseEntity.BodyBuilder builder, InventoryResponse body) {
        return builder.eTag(EntityTags.of(body.getVersion())).body(body);
    }
}
