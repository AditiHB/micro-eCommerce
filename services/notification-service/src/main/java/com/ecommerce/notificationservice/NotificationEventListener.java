package com.ecommerce.notificationservice;

import com.ecommerce.common.events.OrderCreatedEvent;
import com.ecommerce.common.events.PaymentFailedEvent;
import com.ecommerce.common.events.PaymentProcessedEvent;
import com.ecommerce.notificationservice.client.OrderClient;
import com.ecommerce.notificationservice.client.OrderInfo;
import com.ecommerce.notificationservice.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Notification Service's Kafka consumers.
 *
 * Listens to the events relevant to keeping the customer informed:
 * 1. order-created      -> "we received your order"
 * 2. payment-processed  -> "your payment succeeded"
 * 3. payment-failed     -> "your payment failed"
 *
 * Each handler is defensive: a failure to notify never blocks the saga and
 * never throws back into the Kafka container, it is just logged. The offset
 * is acknowledged either way so a single bad event can't poison the consumer.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class NotificationEventListener {

    private final NotificationService notificationService;
    private final OrderClient orderClient;

    @KafkaListener(topics = "order-created", groupId = "notification-group")
    public void handleOrderCreated(@Payload OrderCreatedEvent event, Acknowledgment ack) {
        try {
            log.info("Handling order created event for order: {} - notifying customer {}",
                event.getOrderId(), event.getCustomerId());

            notificationService.notifyOrderCreated(
                event.getEventId(),
                event.getOrderId(),
                event.getCustomerId(),
                event.getProductId(),
                event.getQuantity());
        } catch (Exception e) {
            log.error("Error handling order created event for order: {}", event.getOrderId(), e);
        } finally {
            ack.acknowledge();
        }
    }

    @KafkaListener(topics = "payment-processed", groupId = "notification-group")
    public void handlePaymentProcessed(@Payload PaymentProcessedEvent event, Acknowledgment ack) {
        try {
            log.info("Handling payment processed event for order: {} - notifying customer of successful payment",
                event.getOrderId());

            resolveCustomerId(event.getOrderId()).ifPresentOrElse(
                customerId -> notificationService.notifyPaymentSuccess(
                    event.getEventId(), event.getOrderId(), customerId, event.getAmount()),
                () -> log.warn("Could not resolve customer for order {} - skipping payment-success notification",
                    event.getOrderId()));
        } catch (Exception e) {
            log.error("Error handling payment processed event for order: {}", event.getOrderId(), e);
        } finally {
            ack.acknowledge();
        }
    }

    @KafkaListener(topics = "payment-failed", groupId = "notification-group")
    public void handlePaymentFailed(@Payload PaymentFailedEvent event, Acknowledgment ack) {
        try {
            log.info("Handling payment failed event for order: {} - notifying customer of failed payment",
                event.getOrderId());

            resolveCustomerId(event.getOrderId()).ifPresentOrElse(
                customerId -> notificationService.notifyPaymentFailed(
                    event.getEventId(), event.getOrderId(), customerId, event.getReason()),
                () -> log.warn("Could not resolve customer for order {} - skipping payment-failed notification",
                    event.getOrderId()));
        } catch (Exception e) {
            log.error("Error handling payment failed event for order: {}", event.getOrderId(), e);
        } finally {
            ack.acknowledge();
        }
    }

    /**
     * payment-processed / payment-failed events are published by Payment
     * Service and only carry the orderId, not the customerId. Resolve it
     * from Order Service so the right customer is addressed.
     */
    private Optional<Long> resolveCustomerId(Long orderId) {
        return orderClient.getOrder(orderId).map(OrderInfo::getCustomerId);
    }
}
