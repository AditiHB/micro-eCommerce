package com.ecommerce.orderservice;

import com.ecommerce.common.constants.ApiConstants;
import com.ecommerce.common.dto.PagedResponse;
import com.ecommerce.common.enums.OrderStatus;
import com.ecommerce.common.security.CurrentUser;
import com.ecommerce.common.web.EntityTags;
import com.ecommerce.orderservice.dto.CreateOrderRequest;
import com.ecommerce.orderservice.dto.OrderResponse;
import com.ecommerce.orderservice.service.OrderPlacementService;
import com.ecommerce.orderservice.service.OrderService;
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
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

@RestController
@RequestMapping(ApiConstants.API_PREFIX + ApiConstants.ORDERS_ENDPOINT)
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Order Management", description = "APIs for managing orders")
public class OrderController {

    private final OrderService orderService;
    private final OrderPlacementService placementService;
    private final CurrentUser currentUser;

    @GetMapping
    @Operation(summary = "Get all orders", description = "Back office sees every order; a customer sees only their own")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Successfully retrieved orders",
            content = @Content(schema = @Schema(implementation = PagedResponse.class))),
        @ApiResponse(responseCode = "400", description = "Invalid sort field")
    })
    public ResponseEntity<PagedResponse<OrderResponse>> getAll(
            @Parameter(description = "Page number (0-indexed)", example = "0")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size", example = "20")
            @RequestParam(defaultValue = ApiConstants.DEFAULT_PAGE_SIZE + "") int size,
            @Parameter(description = "Field to sort by: id, createdAt, updatedAt, status, totalAmount, customerId", example = "id")
            @RequestParam(defaultValue = "id") String sortBy) {
        if (currentUser.hasCrossCustomerAccess()) {
            return ResponseEntity.ok(orderService.getAllOrders(page, size, sortBy));
        }
        // A plain USER only ever sees their own orders (OWASP API1 / BOLA).
        Long customerId = currentUser.customerId()
            .orElseThrow(() -> new AccessDeniedException("Token is not bound to a customer"));
        return ResponseEntity.ok(orderService.getOrdersByCustomer(customerId, page, size, sortBy));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get order by ID", description = "The response carries the order's version as its ETag")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Order found",
            content = @Content(schema = @Schema(implementation = OrderResponse.class))),
        @ApiResponse(responseCode = "404", description = "Order not found")
    })
    public ResponseEntity<OrderResponse> getById(
            @Parameter(description = "Order ID", example = "1") @PathVariable Long id) {
        OrderResponse order = orderService.getOrder(id);
        currentUser.requireAccessToCustomer(order.getCustomerId(), "Order", id);
        return withEtag(ResponseEntity.ok(), order);
    }

    /**
     * Places an order. There is no retry or circuit breaker around this method: creating the order is a local
     * database write, and retrying a write whose response was lost would create a second order. Safety comes from
     * the optional {@code Idempotency-Key}: send the same key with the same body and you get the original order back.
     */
    @PostMapping
    // Evaluated first, so a forbidden request never reaches the catalogue or the customer service.
    @PreAuthorize("@currentUser.canAccessCustomer(#request.customerId)")
    @Operation(summary = "Create a new order",
        description = "Prices come from the catalogue, never from the request. Send an Idempotency-Key header to make retries safe.")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "201", description = "Order created (or the original order, if the Idempotency-Key was already used)",
            content = @Content(schema = @Schema(implementation = OrderResponse.class))),
        @ApiResponse(responseCode = "400", description = "Invalid input"),
        @ApiResponse(responseCode = "422", description = "Unknown customer or product, or the Idempotency-Key was reused for a different request"),
        @ApiResponse(responseCode = "503", description = "The catalogue or customer service is unavailable")
    })
    public ResponseEntity<OrderResponse> createOrder(
            @Parameter(description = "Client-generated unique key; repeating it returns the same order instead of a second one")
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody CreateOrderRequest request) {
        log.info("POST /orders - customer {}, {} line(s)", request.getCustomerId(), request.normalizedItems().size());
        OrderService.Placement placement = placementService.placeOrder(request, idempotencyKey);
        OrderResponse order = placement.order();
        ResponseEntity.BodyBuilder response = ResponseEntity.created(URI.create(
                ApiConstants.API_PREFIX + ApiConstants.ORDERS_ENDPOINT + "/" + order.getId()));
        if (placement.replayed()) {
            response.header("Idempotent-Replayed", "true");
        }
        return withEtag(response, order);
    }

    @PostMapping("/{id}/cancel")
    @Operation(summary = "Cancel an order",
        description = "Releases the reserved stock and refunds a captured payment. Cancelling a cancelled order is a no-op; a completed order cannot be cancelled.")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Order cancelled"),
        @ApiResponse(responseCode = "404", description = "Order not found"),
        @ApiResponse(responseCode = "409", description = "The order is already completed or failed"),
        @ApiResponse(responseCode = "412", description = "If-Match does not match the order's current version")
    })
    public ResponseEntity<OrderResponse> cancel(
            @PathVariable Long id,
            @Parameter(description = "Reason shown in the audit trail") @RequestParam(defaultValue = "Cancelled by customer") String reason,
            @RequestHeader(name = HttpHeaders.IF_MATCH, required = false) String ifMatch) {
        OrderResponse current = orderService.getOrder(id);
        currentUser.requireAccessToCustomer(current.getCustomerId(), "Order", id);
        EntityTags.verifyIfMatch(ifMatch, current.getVersion());
        return withEtag(ResponseEntity.ok(), orderService.cancelOrder(id, reason));
    }

    @PutMapping("/{id}/status")
    @Operation(summary = "Update order status (back office)",
        description = "Only CANCELLED (runs the compensations) and FAILED can be set by hand; the saga owns every other transition.")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Order status updated",
            content = @Content(schema = @Schema(implementation = OrderResponse.class))),
        @ApiResponse(responseCode = "404", description = "Order not found"),
        @ApiResponse(responseCode = "409", description = "Not a legal transition from the order's current status")
    })
    public ResponseEntity<OrderResponse> updateOrderStatus(
            @Parameter(description = "Order ID", example = "1") @PathVariable Long id,
            @Parameter(description = "New order status", example = "CANCELLED") @RequestParam OrderStatus status) {
        log.info("PUT /orders/{}/status -> {}", id, status);
        return withEtag(ResponseEntity.ok(), orderService.updateOrderStatus(id, status));
    }

    private static ResponseEntity<OrderResponse> withEtag(ResponseEntity.BodyBuilder builder, OrderResponse order) {
        return builder.eTag(EntityTags.of(order.getVersion())).body(order);
    }
}
