package com.ecommerce.paymentservice;

import com.ecommerce.common.events.InventoryReservedEvent;
import com.ecommerce.common.events.OrderCancelledEvent;
import com.ecommerce.common.events.Topics;
import com.ecommerce.paymentservice.service.PaymentSagaHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Payment service's Kafka entry points - deliberately thin.
 *
 * <ul>
 *   <li>{@code inventory.reserved} - forward step: charge the order's total. The outcome is announced as
 *       {@code payment.processed} or {@code payment.failed}; a processor decline is a result, not an error.</li>
 *   <li>{@code order.cancelled} - compensation: refund the payment if it was captured.</li>
 * </ul>
 *
 * Listeners never catch an exception and acknowledge: they let it propagate and the container's error handler
 * retries with backoff (a processor outage heals) and finally dead-letters the record. All logic - idempotency
 * included - is in {@link PaymentSagaHandler}.
 */
@Component
@RequiredArgsConstructor
public class PaymentEventListener {

    private final PaymentSagaHandler handler;

    @KafkaListener(topics = Topics.INVENTORY_RESERVED, groupId = Topics.GROUP_PAYMENT)
    public void handleInventoryReserved(InventoryReservedEvent event) {
        handler.onInventoryReserved(event);
    }

    @KafkaListener(topics = Topics.ORDER_CANCELLED, groupId = Topics.GROUP_PAYMENT)
    public void handleOrderCancelled(OrderCancelledEvent event) {
        handler.onOrderCancelled(event);
    }
}
