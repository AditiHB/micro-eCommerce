package com.ecommerce.inventoryservice;

import com.ecommerce.common.events.InventoryFailedEvent;
import com.ecommerce.common.events.InventoryReservedEvent;
import com.ecommerce.common.events.OrderCreatedEvent;
import com.ecommerce.common.events.EventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@RequiredArgsConstructor
@Slf4j
public class InventoryEventListener {

    private final InventoryRepository repository;
    private final EventPublisher eventPublisher;

    @KafkaListener(topics = "order-created", groupId = "inventory-group")
    public void handleOrderCreated(@Payload OrderCreatedEvent event, Acknowledgment ack) {
        try {
            log.info("Handling order created event for order: {}", event.getOrderId());

            Optional<Inventory> inventoryOpt = repository.findByProductId(event.getProductId());

            if (inventoryOpt.isPresent() && inventoryOpt.get().getQuantity() >= event.getQuantity()) {
                Inventory inventory = inventoryOpt.get();
                inventory.setQuantity(inventory.getQuantity() - event.getQuantity());
                repository.save(inventory);

                InventoryReservedEvent reservedEvent = new InventoryReservedEvent(event.getOrderId());
                eventPublisher.publishEvent(reservedEvent, "inventory-reserved",
                    event.getEventId(), event.getEventId());

                log.info("Inventory reserved successfully for order: {}", event.getOrderId());
            } else {
                InventoryFailedEvent failedEvent = new InventoryFailedEvent(event.getOrderId());
                eventPublisher.publishEvent(failedEvent, "inventory-failed",
                    event.getEventId(), event.getEventId());

                log.warn("Inventory reservation failed for order: {} - insufficient stock", event.getOrderId());
            }

            ack.acknowledge();
        } catch (Exception e) {
            log.error("Error handling order created event for order: {}", event.getOrderId(), e);
        }
    }
}
