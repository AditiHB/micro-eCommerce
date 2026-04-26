package com.ecommerce.orderservice;

import com.ecommerce.common.events.InventoryFailedEvent;
import com.ecommerce.common.events.PaymentFailedEvent;
import com.ecommerce.common.events.PaymentProcessedEvent;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class OrderEventListener {

    private final OrderRepository repository;

    public OrderEventListener(OrderRepository repository) {
        this.repository = repository;
    }

    @KafkaListener(topics = "payment-processed", groupId = "order-group")
    public void handlePaymentProcessed(PaymentProcessedEvent event) {
        repository.findById(event.getOrderId()).ifPresent(order -> {
            order.setStatus("COMPLETED");
            repository.save(order);
        });
    }

    @KafkaListener(topics = "inventory-failed", groupId = "order-group")
    public void handleInventoryFailed(InventoryFailedEvent event) {
        repository.findById(event.getOrderId()).ifPresent(order -> {
            order.setStatus("CANCELLED");
            repository.save(order);
        });
    }

    @KafkaListener(topics = "payment-failed", groupId = "order-group")
    public void handlePaymentFailed(PaymentFailedEvent event) {
        repository.findById(event.getOrderId()).ifPresent(order -> {
            order.setStatus("CANCELLED");
            repository.save(order);
        });
    }
}
