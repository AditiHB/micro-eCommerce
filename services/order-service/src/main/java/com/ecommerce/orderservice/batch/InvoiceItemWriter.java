package com.ecommerce.orderservice.batch;

import com.ecommerce.common.events.EventPublisher;
import com.ecommerce.common.events.InvoiceGeneratedEvent;
import com.ecommerce.orderservice.Invoice;
import com.ecommerce.orderservice.InvoiceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ItemWriter;
import org.springframework.stereotype.Component;

/**
 * Persists each generated invoice and queues its {@link InvoiceGeneratedEvent}, in the same chunk
 * transaction (chunk size 1, so this is exactly one order per transaction - see
 * {@link InvoiceBatchConfig}). {@link EventPublisher#publish} requires an active transaction
 * ({@code Propagation.MANDATORY}); the chunk-oriented step already provides one.
 */
@Component
@RequiredArgsConstructor
public class InvoiceItemWriter implements ItemWriter<Invoice> {

    private final InvoiceRepository invoiceRepository;
    private final EventPublisher eventPublisher;

    @Override
    public void write(Chunk<? extends Invoice> chunk) {
        for (Invoice invoice : chunk) {
            Invoice saved = invoiceRepository.save(invoice);
            eventPublisher.publish(new InvoiceGeneratedEvent(
                    saved.getId(), saved.getOrderId(), saved.getCustomerId(), saved.getGeneratedAt()));
        }
    }
}
