package com.ecommerce.paymentservice;

import com.ecommerce.common.constants.ApiConstants;
import com.ecommerce.common.dto.PagedResponse;
import com.ecommerce.paymentservice.dto.ProcessPaymentRequest;
import com.ecommerce.paymentservice.dto.PaymentResponse;
import com.ecommerce.paymentservice.service.PaymentService;
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
@RequestMapping(ApiConstants.API_PREFIX + ApiConstants.PAYMENTS_ENDPOINT)
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Payment Management", description = "APIs for managing payments")
public class PaymentController {

    private final PaymentService paymentService;

    /**
     * Get all payments with pagination.
     */
    @GetMapping
    @Operation(summary = "Get all payments", description = "Retrieve all payments with pagination support")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Successfully retrieved payments",
            content = @Content(schema = @Schema(implementation = PagedResponse.class))),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<PagedResponse<PaymentResponse>> getAll(
            @Parameter(description = "Page number (0-indexed)", example = "0")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size", example = "20")
            @RequestParam(defaultValue = ApiConstants.DEFAULT_PAGE_SIZE + "") int size,
            @Parameter(description = "Field to sort by", example = "id")
            @RequestParam(defaultValue = "id") String sortBy) {
        log.info("GET /api/payments - Retrieving payments - page: {}, size: {}, sortBy: {}", page, size, sortBy);
        return ResponseEntity.ok(paymentService.getAllPayments(page, size, sortBy));
    }

    /**
     * Get payment by ID.
     */
    @GetMapping("/{id}")
    @Operation(summary = "Get payment by ID", description = "Retrieve a specific payment by their ID")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Payment found",
            content = @Content(schema = @Schema(implementation = PaymentResponse.class))),
        @ApiResponse(responseCode = "404", description = "Payment not found"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<PaymentResponse> getById(
            @Parameter(description = "Payment ID", example = "1")
            @PathVariable Long id) {
        log.info("GET /api/payments/{} - Retrieving payment", id);
        return ResponseEntity.ok(paymentService.getPayment(id));
    }

    /**
     * Process a new payment.
     */
    @PostMapping
    @Operation(summary = "Process a payment", description = "Process a new payment for an order")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "201", description = "Payment processed successfully",
            content = @Content(schema = @Schema(implementation = PaymentResponse.class))),
        @ApiResponse(responseCode = "400", description = "Invalid input"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<PaymentResponse> processPayment(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Payment processing request", required = true)
            @Valid @RequestBody ProcessPaymentRequest request) {
        log.info("POST /api/payments - Processing payment for order: {}", request.getOrderId());
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(paymentService.processPayment(request));
    }

    /**
     * Refund a payment.
     */
    @PostMapping("/{id}/refund")
    @Operation(summary = "Refund a payment", description = "Process a refund for an existing payment")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Payment refunded successfully",
            content = @Content(schema = @Schema(implementation = PaymentResponse.class))),
        @ApiResponse(responseCode = "404", description = "Payment not found"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<PaymentResponse> refundPayment(
            @Parameter(description = "Payment ID", example = "1")
            @PathVariable Long id) {
        log.info("POST /api/payments/{}/refund - Refunding payment", id);
        return ResponseEntity.ok(paymentService.refundPayment(id));
    }
}
