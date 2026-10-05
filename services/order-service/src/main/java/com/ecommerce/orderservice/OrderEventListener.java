package com.ecommerce.orderservice;

import com.ecommerce.common.events.InventoryFailedEvent;
import com.ecommerce.common.events.InventoryReservedEvent;
import com.ecommerce.common.events.PaymentFailedEvent;
import com.ecommerce.common.events.PaymentProcessedEvent;
import com.ecommerce.common.events.RefundCompletedEvent;
import com.ecommerce.common.events.Topics;
import com.ecommerce.orderservice.service.OrderSagaHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Order service's Kafka entry points - deliberately thin. The saga here is <em>choreography</em>: each service
 * reacts to the events it cares about and emits its own; there is no central orchestrator.
 *
 * <pre>
 *   order.created ---------> inventory reserves ----> inventory.reserved ---> payment charges
 *                                    | fails                                      |
 *                              inventory.failed                          payment.processed / payment.failed
 *   this service:  inventory.reserved -> INVENTORY_RESERVED     payment.processed -> COMPLETED
 *                  inventory.failed / payment.failed -> CANCELLED -> order.cancelled
 *   order.cancelled -> inventory releases its reservation, payment refunds a captured charge
 * </pre>
 *
 * A listener never catches an exception and acknowledges: it lets it propagate, and the container's error
 * handler retries with backoff and finally dead-letters the record (see {@code KafkaEventConfig}). All the logic,
 * including idempotency, is in {@link OrderSagaHandler}, one transaction per event.
 */
@Component
@RequiredArgsConstructor
public class OrderEventListener {

    private final OrderSagaHandler handler;

    @KafkaListener(topics = Topics.INVENTORY_RESERVED, groupId = Topics.GROUP_ORDER)
    public void handleInventoryReserved(InventoryReservedEvent event) {
        handler.onInventoryReserved(event);
    }

    @KafkaListener(topics = Topics.INVENTORY_FAILED, groupId = Topics.GROUP_ORDER)
    public void handleInventoryFailed(InventoryFailedEvent event) {
        handler.onInventoryFailed(event);
    }

    @KafkaListener(topics = Topics.PAYMENT_PROCESSED, groupId = Topics.GROUP_ORDER)
    public void handlePaymentProcessed(PaymentProcessedEvent event) {
        handler.onPaymentProcessed(event);
    }

    @KafkaListener(topics = Topics.PAYMENT_FAILED, groupId = Topics.GROUP_ORDER)
    public void handlePaymentFailed(PaymentFailedEvent event) {
        handler.onPaymentFailed(event);
    }

    @KafkaListener(topics = Topics.REFUND_COMPLETED, groupId = Topics.GROUP_ORDER)
    public void handleRefundCompleted(RefundCompletedEvent event) {
        handler.onRefundCompleted(event);
    }
}
