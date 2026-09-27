package com.ecommerce.orderservice;

import com.ecommerce.common.enums.OrderStatus;
import com.ecommerce.common.events.InventoryFailedEvent;
import com.ecommerce.common.events.PaymentFailedEvent;
import com.ecommerce.common.events.PaymentProcessedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class OrderEventListener {

    private final OrderRepository repository;

    @KafkaListener(topics = "payment-processed", groupId = "order-group")
    public void handlePaymentProcessed(@Payload PaymentProcessedEvent event, Acknowledgment ack) {
        try {
            log.info("Handling payment processed event for order: {}", event.getOrderId());
            repository.findById(event.getOrderId()).ifPresent(order -> {
                order.setStatus(OrderStatus.COMPLETED);
                repository.save(order);
                log.info("Order {} status updated to COMPLETED", order.getId());
            });
            ack.acknowledge();
        } catch (Exception e) {
            log.error("Error handling payment processed event for order: {}", event.getOrderId(), e);
        }
    }

    @KafkaListener(topics = "inventory-failed", groupId = "order-group")
    public void handleInventoryFailed(@Payload InventoryFailedEvent event, Acknowledgment ack) {
        try {
            log.info("Handling inventory failed event for order: {}", event.getOrderId());
            repository.findById(event.getOrderId()).ifPresent(order -> {
                order.setStatus(OrderStatus.CANCELLED);
                repository.save(order);
                log.info("Order {} status updated to CANCELLED due to inventory failure", order.getId());
            });
            ack.acknowledge();
        } catch (Exception e) {
            log.error("Error handling inventory failed event for order: {}", event.getOrderId(), e);
        }
    }

    @KafkaListener(topics = "payment-failed", groupId = "order-group")
    public void handlePaymentFailed(@Payload PaymentFailedEvent event, Acknowledgment ack) {
        try {
            log.info("Handling payment failed event for order: {}", event.getOrderId());
            repository.findById(event.getOrderId()).ifPresent(order -> {
                order.setStatus(OrderStatus.CANCELLED);
                repository.save(order);
                log.info("Order {} status updated to CANCELLED due to payment failure", order.getId());
            });
            ack.acknowledge();
        } catch (Exception e) {
            log.error("Error handling payment failed event for order: {}", event.getOrderId(), e);
        }
    }
}
