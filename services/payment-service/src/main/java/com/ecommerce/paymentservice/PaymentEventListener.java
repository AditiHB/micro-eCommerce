package com.ecommerce.paymentservice;

import com.ecommerce.common.events.InventoryReservedEvent;
import com.ecommerce.common.events.PaymentProcessedEvent;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import java.math.BigDecimal;

@Component
public class PaymentEventListener {

    private final PaymentRepository repository;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    public PaymentEventListener(PaymentRepository repository, KafkaTemplate<String, Object> kafkaTemplate) {
        this.repository = repository;
        this.kafkaTemplate = kafkaTemplate;
    }

    @KafkaListener(topics = "inventory-reserved", groupId = "payment-group")
    public void handleInventoryReserved(InventoryReservedEvent event) {
        // Simulate payment logic
        Payment payment = new Payment();
        payment.setOrderId(event.getOrderId());
        payment.setAmount(new BigDecimal("99.99")); // Static for simplicity
        payment.setStatus("PROCESSED");
        repository.save(payment);

        kafkaTemplate.send("payment-processed", new PaymentProcessedEvent(event.getOrderId()));
    }
}
