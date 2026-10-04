package com.ecommerce.inventoryservice;

import com.ecommerce.common.events.InventoryFailedEvent;
import com.ecommerce.common.events.InventoryReservedEvent;
import com.ecommerce.common.events.InventoryReleasedEvent;
import com.ecommerce.common.events.OrderCreatedEvent;
import com.ecommerce.common.events.PaymentFailedEvent;
import com.ecommerce.common.events.EventPublisher;
import com.ecommerce.inventoryservice.service.InventoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

/**
 * Inventory Event Listener - Handles Saga Pattern with Compensating Transactions
 *
 * Forward Transactions:
 * - handleOrderCreated: Reserve inventory when order is created
 *
 * Compensating Transactions:
 * - handlePaymentFailed: Release reserved inventory when payment fails (Saga rollback)
 *
 * This implements choreography-based Saga pattern where services emit events
 * and other services listen and react, including compensating actions on failure.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class InventoryEventListener {

    private final InventoryService inventoryService;
    private final EventPublisher eventPublisher;

    /**
     * Forward Transaction: Reserve inventory when order is created.
     * Publishes InventoryReservedEvent on success or InventoryFailedEvent on failure.
     */
    @KafkaListener(topics = "order-created", groupId = "inventory-group")
    public void handleOrderCreated(@Payload OrderCreatedEvent event, Acknowledgment ack) {
        try {
            log.info("Handling order created event for order: {} - Reserving inventory for product: {}, quantity: {}",
                event.getOrderId(), event.getProductId(), event.getQuantity());

            boolean reserved = inventoryService
                .reserveStockIfAvailable(event.getProductId(), event.getQuantity())
                .isPresent();

            if (reserved) {
                // Include product and quantity in reserved event for potential compensation
                InventoryReservedEvent reservedEvent = new InventoryReservedEvent(
                    event.getOrderId(),
                    event.getProductId(),
                    event.getQuantity()
                );
                eventPublisher.publishEvent(reservedEvent, "inventory-reserved",
                    event.getEventId(), event.getEventId());

                log.info("✓ Inventory reserved successfully for order: {} (product: {}, qty: {})",
                    event.getOrderId(), event.getProductId(), event.getQuantity());
            } else {
                InventoryFailedEvent failedEvent = new InventoryFailedEvent(event.getOrderId());
                eventPublisher.publishEvent(failedEvent, "inventory-failed",
                    event.getEventId(), event.getEventId());

                log.warn("✗ Inventory reservation failed for order: {} - insufficient stock for product: {}",
                    event.getOrderId(), event.getProductId());
            }

            ack.acknowledge();
        } catch (Exception e) {
            log.error("Error handling order created event for order: {}", event.getOrderId(), e);
        }
    }

    /**
     * Compensating Transaction: Release reserved inventory when payment fails.
     * This is a Saga rollback - reverse the inventory reservation.
     */
    @KafkaListener(topics = "payment-failed", groupId = "inventory-group")
    public void handlePaymentFailed(@Payload PaymentFailedEvent event, Acknowledgment ack) {
        try {
            log.info("Handling payment failed event for order: {} - Releasing inventory (Compensating Transaction)",
                event.getOrderId());

            if (event.getProductId() != null && event.getQuantity() != null) {
                boolean released = inventoryService
                    .releaseStockIfPresent(event.getProductId(), event.getQuantity())
                    .isPresent();

                if (released) {
                    InventoryReleasedEvent releasedEvent = new InventoryReleasedEvent(
                        event.getOrderId(),
                        event.getProductId(),
                        event.getQuantity()
                    );
                    eventPublisher.publishEvent(releasedEvent, "inventory-released",
                        event.getEventId(), event.getEventId());

                    log.info("✓ Inventory released successfully for order: {} (Saga compensation - product: {}, qty: {})",
                        event.getOrderId(), event.getProductId(), event.getQuantity());
                } else {
                    log.warn("⚠ Could not find inventory to release for product: {}", event.getProductId());
                }
            } else {
                log.warn("⚠ Payment failed event missing product/quantity info for order: {}", event.getOrderId());
            }

            ack.acknowledge();
        } catch (Exception e) {
            log.error("Error handling payment failed event for order: {}", event.getOrderId(), e);
        }
    }
}
