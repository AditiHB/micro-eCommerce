package com.ecommerce.notificationservice;

import com.ecommerce.common.constants.ApiConstants;
import com.ecommerce.common.dto.PagedResponse;
import com.ecommerce.notificationservice.dto.NotificationResponse;
import com.ecommerce.notificationservice.service.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Read-only API over the notifications Notification Service has sent.
 * Notifications themselves are produced by reacting to Kafka events, not
 * through this API.
 */
@RestController
@RequestMapping(ApiConstants.API_PREFIX + ApiConstants.NOTIFICATIONS_ENDPOINT)
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Notification Management", description = "APIs for viewing customer notifications")
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping
    @Operation(summary = "Get all notifications", description = "Retrieve all notifications with pagination support")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Successfully retrieved notifications",
            content = @Content(schema = @Schema(implementation = PagedResponse.class))),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<PagedResponse<NotificationResponse>> getAll(
            @Parameter(description = "Page number (0-indexed)", example = "0")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size", example = "20")
            @RequestParam(defaultValue = ApiConstants.DEFAULT_PAGE_SIZE + "") int size,
            @Parameter(description = "Field to sort by", example = "createdAt")
            @RequestParam(defaultValue = "createdAt") String sortBy) {
        log.info("GET /api/notifications - page: {}, size: {}, sortBy: {}", page, size, sortBy);
        return ResponseEntity.ok(notificationService.getAllNotifications(page, size, sortBy));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get notification by ID", description = "Retrieve a specific notification by its ID")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Notification found",
            content = @Content(schema = @Schema(implementation = NotificationResponse.class))),
        @ApiResponse(responseCode = "404", description = "Notification not found"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<NotificationResponse> getById(
            @Parameter(description = "Notification ID", example = "1")
            @PathVariable Long id) {
        log.info("GET /api/notifications/{} - Retrieving notification", id);
        return ResponseEntity.ok(notificationService.getNotification(id));
    }

    @GetMapping("/customer/{customerId}")
    @Operation(summary = "Get notifications for a customer",
        description = "Retrieve all notifications sent to a specific customer, newest first")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Successfully retrieved notifications",
            content = @Content(schema = @Schema(implementation = PagedResponse.class))),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<PagedResponse<NotificationResponse>> getByCustomer(
            @Parameter(description = "Customer ID", example = "1")
            @PathVariable Long customerId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = ApiConstants.DEFAULT_PAGE_SIZE + "") int size) {
        log.info("GET /api/notifications/customer/{} - page: {}, size: {}", customerId, page, size);
        return ResponseEntity.ok(notificationService.getNotificationsByCustomer(customerId, page, size));
    }

    @GetMapping("/order/{orderId}")
    @Operation(summary = "Get notifications for an order",
        description = "Retrieve every notification sent in relation to a specific order")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Successfully retrieved notifications",
            content = @Content(schema = @Schema(implementation = NotificationResponse.class))),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<List<NotificationResponse>> getByOrder(
            @Parameter(description = "Order ID", example = "1001")
            @PathVariable Long orderId) {
        log.info("GET /api/notifications/order/{} - Retrieving notifications", orderId);
        return ResponseEntity.ok(notificationService.getNotificationsByOrder(orderId));
    }
}
