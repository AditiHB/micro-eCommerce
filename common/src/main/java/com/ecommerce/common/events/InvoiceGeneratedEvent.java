package com.ecommerce.common.events;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/** An invoice PDF was rendered, compressed and signed for a completed order. */
@Getter
@Setter
@NoArgsConstructor
@EventSchema(type = "invoice.generated", topic = Topics.INVOICE_GENERATED)
public class InvoiceGeneratedEvent extends DomainEvent {

    private Long invoiceId;
    private Long orderId;
    private Long customerId;
    private Instant generatedAt;

    public InvoiceGeneratedEvent(Long invoiceId, Long orderId, Long customerId, Instant generatedAt) {
        super(String.valueOf(orderId), "Invoice");
        this.invoiceId = invoiceId;
        this.orderId = orderId;
        this.customerId = customerId;
        this.generatedAt = generatedAt;
    }
}
