package com.ecommerce.inventoryservice;

import com.ecommerce.common.events.OrderCancelledEvent;
import com.ecommerce.common.events.OrderCreatedEvent;
import com.ecommerce.common.events.Topics;
import com.ecommerce.inventoryservice.service.InventorySagaHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Inventory service's Kafka entry points - deliberately thin.
 *
 * <ul>
 *   <li>{@code order.created}  - forward step: reserve stock for the order, emit reserved or failed.</li>
 *   <li>{@code order.cancelled} - compensation: release whatever the order holds. The order service is the only
 *       one that decides to cancel; everything else (payment declined, no stock, saga deadline, a customer) arrives
 *       here as that one event, so there is exactly one compensation path.</li>
 * </ul>
 *
 * Listeners never catch an exception and acknowledge: they let it propagate and the container's error handler
 * retries with backoff and finally dead-letters the record. All logic - idempotency included - is in
 * {@link InventorySagaHandler}.
 */
@Component
@RequiredArgsConstructor
public class InventoryEventListener {

    private final InventorySagaHandler handler;

    @KafkaListener(topics = Topics.ORDER_CREATED, groupId = Topics.GROUP_INVENTORY)
    public void handleOrderCreated(OrderCreatedEvent event) {
        handler.onOrderCreated(event);
    }

    @KafkaListener(topics = Topics.ORDER_CANCELLED, groupId = Topics.GROUP_INVENTORY)
    public void handleOrderCancelled(OrderCancelledEvent event) {
        handler.onOrderCancelled(event);
    }
}
