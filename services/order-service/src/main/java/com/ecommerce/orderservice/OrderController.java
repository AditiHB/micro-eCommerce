package com.ecommerce.orderservice;

import com.ecommerce.common.constants.ApiConstants;
import com.ecommerce.common.dto.PagedResponse;
import com.ecommerce.common.enums.OrderStatus;
import com.ecommerce.orderservice.dto.CreateOrderRequest;
import com.ecommerce.orderservice.dto.OrderResponse;
import com.ecommerce.orderservice.service.OrderService;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping(ApiConstants.API_PREFIX + ApiConstants.ORDERS_ENDPOINT)
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Order Management", description = "APIs for managing orders")
public class OrderController {

    private final OrderService orderService;

    /**
     * Get all orders with pagination.
     */
    @GetMapping
    @Operation(summary = "Get all orders", description = "Retrieve all orders with pagination support")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Successfully retrieved orders",
            content = @Content(schema = @Schema(implementation = PagedResponse.class))),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<PagedResponse<OrderResponse>> getAll(
            @Parameter(description = "Page number (0-indexed)", example = "0")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size", example = "20")
            @RequestParam(defaultValue = ApiConstants.DEFAULT_PAGE_SIZE + "") int size,
            @Parameter(description = "Field to sort by", example = "id")
            @RequestParam(defaultValue = "id") String sortBy) {
        log.info("GET /api/orders - Retrieving orders - page: {}, size: {}, sortBy: {}", page, size, sortBy);
        return ResponseEntity.ok(orderService.getAllOrders(page, size, sortBy));
    }

    /**
     * Get order by ID.
     */
    @GetMapping("/{id}")
    @Operation(summary = "Get order by ID", description = "Retrieve a specific order by their ID")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Order found",
            content = @Content(schema = @Schema(implementation = OrderResponse.class))),
        @ApiResponse(responseCode = "404", description = "Order not found"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<OrderResponse> getById(
            @Parameter(description = "Order ID", example = "1")
            @PathVariable Long id) {
        log.info("GET /api/orders/{} - Retrieving order", id);
        return ResponseEntity.ok(orderService.getOrder(id));
    }

    /**
     * Create a new order with resilience patterns.
     */
    @PostMapping
    @CircuitBreaker(name = "orderService", fallbackMethod = "createOrderFallback")
    @Retry(name = "orderService")
    @Operation(summary = "Create a new order", description = "Create a new order with resilience patterns (circuit breaker & retry)")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "201", description = "Order created successfully",
            content = @Content(schema = @Schema(implementation = OrderResponse.class))),
        @ApiResponse(responseCode = "400", description = "Invalid input"),
        @ApiResponse(responseCode = "503", description = "Service unavailable (circuit breaker open)")
    })
    public ResponseEntity<OrderResponse> createOrder(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Order creation request", required = true)
            @Valid @RequestBody CreateOrderRequest request) {
        log.info("POST /api/orders - Creating new order for customer: {}", request.getCustomerId());
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(orderService.createOrder(request));
    }

    /**
     * Update order status.
     */
    @PutMapping("/{id}/status")
    @Operation(summary = "Update order status", description = "Update the status of an existing order")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Order status updated successfully",
            content = @Content(schema = @Schema(implementation = OrderResponse.class))),
        @ApiResponse(responseCode = "400", description = "Invalid status"),
        @ApiResponse(responseCode = "404", description = "Order not found"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<OrderResponse> updateOrderStatus(
            @Parameter(description = "Order ID", example = "1")
            @PathVariable Long id,
            @Parameter(description = "New order status", example = "PENDING")
            @RequestParam OrderStatus status) {
        log.info("PUT /api/orders/{}/status - Updating status to {}", id, status);
        return ResponseEntity.ok(orderService.updateOrderStatus(id, status));
    }

    /**
     * Fallback method for createOrder when circuit breaker is open.
     */
    public ResponseEntity<OrderResponse> createOrderFallback(
            CreateOrderRequest request,
            Exception e) {
        log.warn("Circuit breaker opened for order creation: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
            .body(OrderResponse.builder()
                .status(OrderStatus.FAILED)
                .build());
    }
}
