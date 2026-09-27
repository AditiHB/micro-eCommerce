package com.ecommerce.orderservice;

import com.ecommerce.common.enums.OrderStatus;
import com.ecommerce.orderservice.dto.CreateOrderRequest;
import com.ecommerce.orderservice.dto.OrderResponse;
import com.ecommerce.orderservice.service.OrderService;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
@Slf4j
public class OrderController {

    private final OrderService orderService;

    /**
     * Get all orders.
     */
    @GetMapping
    public ResponseEntity<List<OrderResponse>> getAll() {
        log.info("GET /api/orders - Retrieving all orders");
        return ResponseEntity.ok(orderService.getAllOrders());
    }

    /**
     * Get order by ID.
     */
    @GetMapping("/{id}")
    public ResponseEntity<OrderResponse> getById(@PathVariable Long id) {
        log.info("GET /api/orders/{} - Retrieving order", id);
        return ResponseEntity.ok(orderService.getOrder(id));
    }

    /**
     * Create a new order with resilience patterns.
     */
    @PostMapping
    @CircuitBreaker(name = "orderService", fallbackMethod = "createOrderFallback")
    @Retry(name = "orderService")
    public ResponseEntity<OrderResponse> createOrder(@Valid @RequestBody CreateOrderRequest request) {
        log.info("POST /api/orders - Creating new order for customer: {}", request.getCustomerId());
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(orderService.createOrder(request));
    }

    /**
     * Update order status.
     */
    @PutMapping("/{id}/status")
    public ResponseEntity<OrderResponse> updateOrderStatus(
            @PathVariable Long id,
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
