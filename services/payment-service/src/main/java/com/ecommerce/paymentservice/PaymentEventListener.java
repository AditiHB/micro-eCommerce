package com.ecommerce.paymentservice;

import com.ecommerce.common.enums.PaymentStatus;
import com.ecommerce.common.events.InventoryReservedEvent;
import com.ecommerce.common.events.OrderCancelledEvent;
import com.ecommerce.common.events.PaymentProcessedEvent;
import com.ecommerce.common.events.PaymentFailedEvent;
import com.ecommerce.common.events.RefundInitiatedEvent;
import com.ecommerce.common.events.RefundCompletedEvent;
import com.ecommerce.common.events.EventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * Payment Event Listener - Handles Saga Pattern with Compensating Transactions
 *
 * Forward Transactions:
 * - handleInventoryReserved: Process payment when inventory is reserved
 *
 * Compensating Transactions:
 * - handleOrderCancelled: Refund payment when order is cancelled (Saga rollback)
 *
 * This implements choreography-based Saga pattern where payment is the critical
 * resource that must be refunded if subsequent steps fail.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentEventListener {

    private final PaymentRepository repository;
    private final EventPublisher eventPublisher;

    /**
     * Forward Transaction: Process payment when inventory is reserved.
     * Publishes PaymentProcessedEvent on success or PaymentFailedEvent on failure.
     */
    @KafkaListener(topics = "inventory-reserved", groupId = "payment-group")
    public void handleInventoryReserved(@Payload InventoryReservedEvent event, Acknowledgment ack) {
        try {
            log.info("Handling inventory reserved event for order: {} - Processing payment", event.getOrderId());

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

            log.info("✓ Payment processed successfully for order: {} (amount: {})",
                event.getOrderId(), savedPayment.getAmount());
            ack.acknowledge();
        } catch (Exception e) {
            log.error("Error handling inventory reserved event for order: {}", event.getOrderId(), e);

            // Include product/quantity in payment failed event for inventory compensation
            PaymentFailedEvent failedEvent = new PaymentFailedEvent(
                event.getOrderId(),
                event.getProductId(),
                event.getQuantity(),
                "Payment processing exception: " + e.getMessage()
            );
            eventPublisher.publishEvent(failedEvent, "payment-failed",
                event.getEventId(), event.getEventId());

            log.warn("✗ Payment processing failed for order: {} - Triggering compensation", event.getOrderId());
        }
    }

    /**
     * Compensating Transaction: Refund payment when order is cancelled.
     * This is a Saga rollback - reverse the payment charge.
     */
    @KafkaListener(topics = "order-cancelled", groupId = "payment-group")
    public void handleOrderCancelled(@Payload OrderCancelledEvent event, Acknowledgment ack) {
        try {
            log.info("Handling order cancelled event for order: {} - Processing refund (Compensating Transaction)",
                event.getOrderId());

            // Find the payment for this order
            Optional<Payment> paymentOpt = repository.findByOrderId(event.getOrderId());

            if (paymentOpt.isPresent()) {
                Payment payment = paymentOpt.get();

                // Only refund if payment was actually processed
                if (payment.getStatus() == PaymentStatus.PROCESSED) {
                    // Update payment status to REFUNDED
                    payment.setStatus(PaymentStatus.REFUNDED);
                    repository.save(payment);

                    // Publish refund completed event
                    RefundCompletedEvent refundCompletedEvent = new RefundCompletedEvent(
                        event.getOrderId(),
                        payment.getId(),
                        payment.getAmount()
                    );
                    eventPublisher.publishEvent(refundCompletedEvent, "refund-completed",
                        event.getEventId(), event.getEventId());

                    log.info("✓ Payment refunded successfully for order: {} (amount: {}, reason: {})",
                        event.getOrderId(), payment.getAmount(), event.getReason());
                } else {
                    log.warn("⚠ Payment for order: {} is not in PROCESSED state, status: {}",
                        event.getOrderId(), payment.getStatus());
                }
            } else {
                log.warn("⚠ No payment found for order: {} - Cannot process refund", event.getOrderId());
            }

            ack.acknowledge();
        } catch (Exception e) {
            log.error("Error handling order cancelled event for order: {}", event.getOrderId(), e);
        }
    }
}
