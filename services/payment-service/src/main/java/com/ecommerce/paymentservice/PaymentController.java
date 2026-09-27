package com.ecommerce.paymentservice;

import com.ecommerce.paymentservice.dto.ProcessPaymentRequest;
import com.ecommerce.paymentservice.dto.PaymentResponse;
import com.ecommerce.paymentservice.service.PaymentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
@Slf4j
public class PaymentController {

    private final PaymentService paymentService;

    /**
     * Get all payments.
     */
    @GetMapping
    public ResponseEntity<List<PaymentResponse>> getAll() {
        log.info("GET /api/payments - Retrieving all payments");
        return ResponseEntity.ok(paymentService.getAllPayments());
    }

    /**
     * Get payment by ID.
     */
    @GetMapping("/{id}")
    public ResponseEntity<PaymentResponse> getById(@PathVariable Long id) {
        log.info("GET /api/payments/{} - Retrieving payment", id);
        return ResponseEntity.ok(paymentService.getPayment(id));
    }

    /**
     * Process a new payment.
     */
    @PostMapping
    public ResponseEntity<PaymentResponse> processPayment(@Valid @RequestBody ProcessPaymentRequest request) {
        log.info("POST /api/payments - Processing payment for order: {}", request.getOrderId());
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(paymentService.processPayment(request));
    }

    /**
     * Refund a payment.
     */
    @PostMapping("/{id}/refund")
    public ResponseEntity<PaymentResponse> refundPayment(@PathVariable Long id) {
        log.info("POST /api/payments/{}/refund - Refunding payment", id);
        return ResponseEntity.ok(paymentService.refundPayment(id));
    }
}
