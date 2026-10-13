package com.ecommerce.orderservice.batch;

import com.ecommerce.orderservice.Invoice;
import com.ecommerce.orderservice.Order;
import com.ecommerce.orderservice.client.CustomerInfo;
import com.ecommerce.orderservice.client.InvoiceCustomerClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Turns one completed order into an {@link Invoice}: look up the customer, render the PDF, hash and sign
 * the canonical (uncompressed) bytes, then compress them three ways. The row is not persisted here - the
 * {@link InvoiceItemWriter} does that, inside the same chunk transaction as the outbox write.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class InvoiceItemProcessor implements ItemProcessor<Order, Invoice> {

    private final InvoiceCustomerClient customerClient;
    private final InvoicePdfRenderer pdfRenderer;
    private final InvoiceCompressor compressor;
    private final InvoiceSigner signer;

    @Override
    public Invoice process(Order order) {
        CustomerInfo customer = customerClient.find(order.getCustomerId())
                .orElseThrow(() -> new IllegalStateException(
                        "Order " + order.getId() + " names customer " + order.getCustomerId() + ", which does not exist"));

        byte[] pdf = pdfRenderer.render(order, customer);
        byte[] signature = signer.sign(pdf);

        log.info("Generated invoice for order {} ({} bytes uncompressed)", order.getId(), pdf.length);

        return Invoice.builder()
                .orderId(order.getId())
                .customerId(order.getCustomerId())
                .pdfGzip(compressor.gzip(pdf))
                .pdfZstd(compressor.zstd(pdf))
                .pdfBrotli(compressor.brotli(pdf))
                .signature(signature)
                .signingKeyId(InvoiceSigner.SIGNING_KEY_ID)
                .contentSha256(InvoiceSigner.sha256Hex(pdf))
                .generatedAt(Instant.now())
                .status("GENERATED")
                .build();
    }
}
