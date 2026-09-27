package com.ecommerce.paymentservice;

import com.ecommerce.common.enums.PaymentStatus;
import com.ecommerce.common.events.InventoryReservedEvent;
import com.ecommerce.common.events.PaymentProcessedEvent;
import com.ecommerce.common.events.PaymentFailedEvent;
import com.ecommerce.common.events.EventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentEventListener {

    private final PaymentRepository repository;
    private final EventPublisher eventPublisher;

    @KafkaListener(topics = "inventory-reserved", groupId = "payment-group")
    public void handleInventoryReserved(@Payload InventoryReservedEvent event, Acknowledgment ack) {
        try {
            log.info("Handling inventory reserved event for order: {}", event.getOrderId());

            Payment payment = new Payment();
            payment.setOrderId(event.getOrderId());
            payment.setAmount(new BigDecimal("99.99"));
            payment.setStatus(PaymentStatus.PROCESSED);
            Payment savedPayment = repository.save(payment);

            PaymentProcessedEvent processedEvent = new PaymentProcessedEvent(
                savedPayment.getId(),
                event.getOrderId(),
                savedPayment.getAmount()
            );

            eventPublisher.publishEvent(processedEvent, "payment-processed",
                event.getEventId(), event.getEventId());

            log.info("Payment processed successfully for order: {}", event.getOrderId());
            ack.acknowledge();
        } catch (Exception e) {
            log.error("Error handling inventory reserved event for order: {}", event.getOrderId(), e);

            PaymentFailedEvent failedEvent = new PaymentFailedEvent(event.getOrderId());
            eventPublisher.publishEvent(failedEvent, "payment-failed",
                event.getEventId(), event.getEventId());
        }
    }
}
