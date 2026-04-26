package com.ecommerce.inventoryservice;

import com.ecommerce.common.events.InventoryFailedEvent;
import com.ecommerce.common.events.InventoryReservedEvent;
import com.ecommerce.common.events.OrderCreatedEvent;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class InventoryEventListener {

    private final InventoryRepository repository;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    public InventoryEventListener(InventoryRepository repository, KafkaTemplate<String, Object> kafkaTemplate) {
        this.repository = repository;
        this.kafkaTemplate = kafkaTemplate;
    }

    @KafkaListener(topics = "order-created", groupId = "inventory-group")
    public void handleOrderCreated(OrderCreatedEvent event) {
        Optional<Inventory> inventoryOpt = repository.findByProductId(event.getProductId());

        if (inventoryOpt.isPresent() && inventoryOpt.get().getQuantity() >= event.getQuantity()) {
            Inventory inventory = inventoryOpt.get();
            inventory.setQuantity(inventory.getQuantity() - event.getQuantity());
            repository.save(inventory);

            kafkaTemplate.send("inventory-reserved", new InventoryReservedEvent(event.getOrderId()));
        } else {
            kafkaTemplate.send("inventory-failed", new InventoryFailedEvent(event.getOrderId()));
        }
    }
}
