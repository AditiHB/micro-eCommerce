package com.ecommerce.productservice.listener;

import com.ecommerce.productservice.event.InventoryReleasedEvent;
import com.ecommerce.productservice.event.InventoryReservedEvent;
import com.ecommerce.productservice.service.ProductService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class InventoryEventListener {
    private final ProductService productService;

    @KafkaListener(topics = "inventory-events", groupId = "product-group", containerFactory = "kafkaListenerContainerFactory")
    public void onInventoryReservedEvent(InventoryReservedEvent event) {
        log.info("Received InventoryReservedEvent for product: {}, quantity: {}",
                event.getProductId(), event.getQuantityReserved());
        try {
            productService.reserveInventory(event.getProductId(), event.getQuantityReserved());
            log.info("Successfully reserved inventory for product: {}", event.getProductId());
        } catch (Exception e) {
            log.error("Error processing InventoryReservedEvent for product: {}", event.getProductId(), e);
        }
    }

    @KafkaListener(topics = "inventory-events", groupId = "product-group", containerFactory = "kafkaListenerContainerFactory")
    public void onInventoryReleasedEvent(InventoryReleasedEvent event) {
        log.info("Received InventoryReleasedEvent for product: {}, quantity: {}",
                event.getProductId(), event.getQuantityReleased());
        try {
            productService.releaseInventory(event.getProductId(), event.getQuantityReleased());
            log.info("Successfully released inventory for product: {}", event.getProductId());
        } catch (Exception e) {
            log.error("Error processing InventoryReleasedEvent for product: {}", event.getProductId(), e);
        }
    }
}
