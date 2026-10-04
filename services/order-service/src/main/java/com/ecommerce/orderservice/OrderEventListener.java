package com.ecommerce.orderservice;

import com.ecommerce.common.enums.OrderStatus;
import com.ecommerce.common.events.InventoryFailedEvent;
import com.ecommerce.common.events.PaymentFailedEvent;
import com.ecommerce.common.events.PaymentProcessedEvent;
import com.ecommerce.common.events.OrderCancelledEvent;
import com.ecommerce.common.events.RefundCompletedEvent;
import com.ecommerce.common.events.DlqPublisher;
import com.ecommerce.common.events.EventPublisher;
import com.ecommerce.orderservice.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

/**
 * Order Event Listener - Saga Orchestrator for Distributed Transaction
 *
 * Happy Path (Forward Transactions):
 * 1. OrderCreatedEvent (from Service) → Inventory Service reserves stock
 * 2. InventoryReservedEvent → Payment Service processes payment
 * 3. PaymentProcessedEvent → Order marked COMPLETED
 *
 * Failure Path (Compensating Transactions):
 * If InventoryFailed → Cancel order, trigger compensation
 * If PaymentFailed → Publish OrderCancelledEvent, Payment refunds, Inventory releases
 * If RefundCompleted → Order fully cancelled with all compensations done
 *
 * This implements the Saga pattern - distributed transaction with compensating actions.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OrderEventListener {

    private final OrderRepository repository;
    private final OrderService orderService;
    private final EventPublisher eventPublisher;
    private final DlqPublisher dlqPublisher;

    /**
     * Happy Path: Order successfully completed after payment.
     */
    @KafkaListener(topics = "payment-processed", groupId = "order-group")
    public void handlePaymentProcessed(@Payload PaymentProcessedEvent event, Acknowledgment ack) {
        try {
            log.info("Handling payment processed event for order: {} - Saga moving to COMPLETED state", event.getOrderId());
            orderService.updateOrderStatusIfPresent(event.getOrderId(), OrderStatus.COMPLETED)
                .ifPresent(order -> log.info("✓ Order {} successfully COMPLETED - Payment processed for amount: {}",
                    order.getId(), event.getAmount()));
            ack.acknowledge();
        } catch (Exception e) {
            log.error("Error handling payment processed event for order: {}", event.getOrderId(), e);
            dlqPublisher.publish(event, "payment-processed", e);
            ack.acknowledge();
        }
    }

    /**
     * Failure Path 1: Inventory reservation failed.
     * Triggers compensation by publishing OrderCancelledEvent.
     */
    @KafkaListener(topics = "inventory-failed", groupId = "order-group")
    public void handleInventoryFailed(@Payload InventoryFailedEvent event, Acknowledgment ack) {
        try {
            log.info("Handling inventory failed event for order: {} - Saga triggered CANCELLATION", event.getOrderId());
            orderService.updateOrderStatusIfPresent(event.getOrderId(), OrderStatus.CANCELLED)
                .ifPresent(order -> {
                    // Publish OrderCancelledEvent to trigger compensating transactions
                    OrderCancelledEvent cancelledEvent = new OrderCancelledEvent(
                        order.getId(),
                        "Inventory reservation failed"
                    );
                    eventPublisher.publishEvent(cancelledEvent, "order-cancelled",
                        event.getEventId(), event.getEventId());

                    log.warn("✗ Order {} CANCELLED due to inventory failure - Compensating transactions triggered", order.getId());
                });
            ack.acknowledge();
        } catch (Exception e) {
            log.error("Error handling inventory failed event for order: {}", event.getOrderId(), e);
            dlqPublisher.publish(event, "inventory-failed", e);
            ack.acknowledge();
        }
    }

    /**
     * Failure Path 2: Payment processing failed.
     * Triggers compensation by publishing OrderCancelledEvent.
     * This causes: Payment refund + Inventory release.
     */
    @KafkaListener(topics = "payment-failed", groupId = "order-group")
    public void handlePaymentFailed(@Payload PaymentFailedEvent event, Acknowledgment ack) {
        try {
            log.info("Handling payment failed event for order: {} - Saga triggered CANCELLATION (Compensating Transactions)",
                event.getOrderId());
            orderService.updateOrderStatusIfPresent(event.getOrderId(), OrderStatus.CANCELLED)
                .ifPresent(order -> {
                    // Publish OrderCancelledEvent to trigger compensating transactions
                    OrderCancelledEvent cancelledEvent = new OrderCancelledEvent(
                        order.getId(),
                        event.getReason() != null ? event.getReason() : "Payment processing failed"
                    );
                    eventPublisher.publishEvent(cancelledEvent, "order-cancelled",
                        event.getEventId(), event.getEventId());

                    log.warn("✗ Order {} CANCELLED due to payment failure - Compensating transactions triggered: " +
                        "Payment refund + Inventory release", order.getId());
                });
            ack.acknowledge();
        } catch (Exception e) {
            log.error("Error handling payment failed event for order: {}", event.getOrderId(), e);
            dlqPublisher.publish(event, "payment-failed", e);
            ack.acknowledge();
        }
    }

    /**
     * Compensation Complete: All compensating transactions completed.
     * Order is now fully rolled back to initial state.
     */
    @KafkaListener(topics = "refund-completed", groupId = "order-group")
    public void handleRefundCompleted(@Payload RefundCompletedEvent event, Acknowledgment ack) {
        try {
            log.info("Handling refund completed event for order: {} - All compensating transactions complete",
                event.getOrderId());
            repository.findById(event.getOrderId()).ifPresent(order -> {
                log.info("✓ Saga COMPENSATED for order {}: Payment refunded ({}), Inventory released, Order fully cancelled",
                    order.getId(), event.getRefundAmount());
            });
            ack.acknowledge();
        } catch (Exception e) {
            log.error("Error handling refund completed event for order: {}", event.getOrderId(), e);
        }
    }
}
