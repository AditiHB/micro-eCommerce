package com.ecommerce.orderservice.service;

import com.ecommerce.common.crypto.JweHelper;
import com.ecommerce.common.exception.ConflictException;
import com.ecommerce.common.exception.ResourceNotFoundException;
import com.ecommerce.orderservice.Invoice;
import com.ecommerce.orderservice.InvoiceRepository;
import com.ecommerce.orderservice.Order;
import com.ecommerce.orderservice.OrderLine;
import com.ecommerce.orderservice.OrderRepository;
import com.ecommerce.orderservice.dto.InvoiceDownloadEnvelope;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.security.interfaces.RSAPublicKey;
import java.util.Base64;
import java.util.zip.GZIPInputStream;

/**
 * Builds the invoice download response: the order details and the invoice PDF, together in one JSON
 * envelope, JWE-encrypted for whoever holds the matching private key (see
 * {@code InvoiceDownloadProperties} - a single platform-wide keypair for this worked example, not a
 * real per-customer key-exchange system).
 *
 * <p>Always serves the stored gzip copy, decompressed here - gzip needs no native library, and which of
 * the three stored compressed variants is served is a separate concern from demonstrating JWE.
 */
@Service
@RequiredArgsConstructor
public class InvoiceDownloadService {

    private final OrderRepository orderRepository;
    private final InvoiceRepository invoiceRepository;
    private final ObjectMapper objectMapper;
    private final RSAPublicKey invoiceDownloadPublicKey;

    public String buildEncryptedDownload(Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order", orderId));
        Invoice invoice = invoiceRepository.findByOrderId(orderId)
                .orElseThrow(() -> new ConflictException(
                        "No invoice has been generated yet for order " + orderId, "INVOICE_NOT_READY"));

        byte[] pdf = gunzip(invoice.getPdfGzip());

        InvoiceDownloadEnvelope envelope = new InvoiceDownloadEnvelope(
                new InvoiceDownloadEnvelope.OrderDetails(
                        order.getId(),
                        order.getCustomerId(),
                        order.getTotalAmount(),
                        order.getCurrency(),
                        order.getLines().stream().map(InvoiceDownloadService::toLine).toList()),
                Base64.getEncoder().encodeToString(pdf),
                invoice.getContentSha256(),
                Base64.getEncoder().encodeToString(invoice.getSignature()));

        return JweHelper.encryptCompact(toJsonBytes(envelope), invoiceDownloadPublicKey);
    }

    private static InvoiceDownloadEnvelope.Line toLine(OrderLine line) {
        return new InvoiceDownloadEnvelope.Line(line.getProductId(), line.getQuantity(), line.getUnitPrice());
    }

    private byte[] toJsonBytes(InvoiceDownloadEnvelope envelope) {
        try {
            return objectMapper.writeValueAsBytes(envelope);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize the invoice download envelope", e);
        }
    }

    private static byte[] gunzip(byte[] gzipped) {
        try (GZIPInputStream in = new GZIPInputStream(new java.io.ByteArrayInputStream(gzipped))) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            in.transferTo(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to decompress the stored invoice PDF", e);
        }
    }
}
