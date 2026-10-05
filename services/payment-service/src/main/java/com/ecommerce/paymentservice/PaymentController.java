package com.ecommerce.paymentservice;

import com.ecommerce.common.constants.ApiConstants;
import com.ecommerce.common.dto.PagedResponse;
import com.ecommerce.common.web.EntityTags;
import com.ecommerce.paymentservice.dto.PaymentResponse;
import com.ecommerce.paymentservice.service.PaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Back-office view of payments. There is deliberately no endpoint that creates a charge: money is taken only by
 * the order saga, for the order's own total, so no caller can pick an amount or charge an order twice.
 */
@RestController
@RequestMapping(ApiConstants.API_PREFIX + ApiConstants.PAYMENTS_ENDPOINT)
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Payment Management", description = "Read payments and refund captured ones; charges are made by the order saga")
public class PaymentController {

    private final PaymentService paymentService;

    @GetMapping
    @Operation(summary = "Get all payments", description = "Retrieve all payments with pagination support")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Successfully retrieved payments",
            content = @Content(schema = @Schema(implementation = PagedResponse.class))),
        @ApiResponse(responseCode = "400", description = "Invalid sort field")
    })
    public ResponseEntity<PagedResponse<PaymentResponse>> getAll(
            @Parameter(description = "Page number (0-indexed)", example = "0")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size", example = "20")
            @RequestParam(defaultValue = ApiConstants.DEFAULT_PAGE_SIZE + "") int size,
            @Parameter(description = "Field to sort by: id, orderId, amount, status, createdAt, updatedAt", example = "id")
            @RequestParam(defaultValue = "id") String sortBy) {
        log.info("GET /payments - page: {}, size: {}, sortBy: {}", page, size, sortBy);
        return ResponseEntity.ok(paymentService.getAllPayments(page, size, sortBy));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get payment by ID")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Payment found",
            content = @Content(schema = @Schema(implementation = PaymentResponse.class))),
        @ApiResponse(responseCode = "404", description = "Payment not found")
    })
    public ResponseEntity<PaymentResponse> getById(@Parameter(description = "Payment ID", example = "1") @PathVariable Long id) {
        return withEtag(paymentService.getPayment(id));
    }

    @GetMapping("/order/{orderId}")
    @Operation(summary = "Get the payment of an order", description = "There is at most one payment per order")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Payment found",
            content = @Content(schema = @Schema(implementation = PaymentResponse.class))),
        @ApiResponse(responseCode = "404", description = "The order has no payment (yet)")
    })
    public ResponseEntity<PaymentResponse> getByOrder(@Parameter(description = "Order ID", example = "1") @PathVariable Long orderId) {
        return withEtag(paymentService.getPaymentByOrder(orderId));
    }

    @PostMapping("/{id}/refund")
    @Operation(summary = "Refund a payment",
        description = "Gives captured money back through the processor and announces refund.completed. Only a CAPTURED payment can be refunded.")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Payment refunded",
            content = @Content(schema = @Schema(implementation = PaymentResponse.class))),
        @ApiResponse(responseCode = "404", description = "Payment not found"),
        @ApiResponse(responseCode = "409", description = "The payment is not in a refundable state (never captured, or already refunded)"),
        @ApiResponse(responseCode = "412", description = "If-Match does not match the payment's current version"),
        @ApiResponse(responseCode = "422", description = "The processor refused the refund")
    })
    public ResponseEntity<PaymentResponse> refundPayment(
            @Parameter(description = "Payment ID", example = "1") @PathVariable Long id,
            @RequestHeader(name = HttpHeaders.IF_MATCH, required = false) String ifMatch) {
        log.info("POST /payments/{}/refund", id);
        if (ifMatch != null && !ifMatch.isBlank()) {
            EntityTags.verifyIfMatch(ifMatch, paymentService.getPayment(id).getVersion());
        }
        return withEtag(paymentService.refundPayment(id));
    }

    private static ResponseEntity<PaymentResponse> withEtag(PaymentResponse payment) {
        return ResponseEntity.ok().eTag(EntityTags.of(payment.getVersion())).body(payment);
    }
}
