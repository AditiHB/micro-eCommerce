package com.ecommerce.orderservice.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * What the invoice download endpoint JWE-encrypts as one unit: the order details plus the invoice PDF
 * itself (base64, since JSON has no binary type) and enough to verify it independently (the signature
 * and the hash it was computed over). Encrypting the whole envelope - not just the PDF - means a caller
 * never needs a second, unencrypted call to learn what they are looking at.
 */
public record InvoiceDownloadEnvelope(
        OrderDetails orderDetails,
        String pdfBase64,
        String contentSha256,
        String signatureBase64) {

    public record OrderDetails(
            Long orderId,
            Long customerId,
            BigDecimal totalAmount,
            String currency,
            List<Line> lines) {
    }

    public record Line(String productId, int quantity, BigDecimal unitPrice) {
    }
}
