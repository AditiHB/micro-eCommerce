package com.ecommerce.notificationservice;

import com.ecommerce.common.events.OrderCreatedEvent;
import com.ecommerce.common.events.PaymentFailedEvent;
import com.ecommerce.common.events.PaymentProcessedEvent;
import com.ecommerce.common.events.Topics;
import com.ecommerce.notificationservice.service.NotificationIntake;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Notification service's Kafka consumers - the events that keep the customer informed:
 * {@code order.created} ("we received your order"), {@code payment.processed} ("your payment succeeded") and
 * {@code payment.failed} ("your payment failed").
 *
 * <p>A handler that fails does NOT swallow the error and acknowledge (which used to lose the notification for
 * good): it lets the exception propagate, and the container's error handler retries with backoff and finally
 * dead-letters the record, where it can be inspected and replayed. A failure to <em>deliver</em> an email is a
 * separate matter, retried by the dispatcher and never visible here.
 */
@Component
@RequiredArgsConstructor
public class NotificationEventListener {

    private final NotificationIntake intake;

    @KafkaListener(topics = Topics.ORDER_CREATED, groupId = Topics.GROUP_NOTIFICATION)
    public void handleOrderCreated(OrderCreatedEvent event) {
        intake.onOrderCreated(event);
    }

    @KafkaListener(topics = Topics.PAYMENT_PROCESSED, groupId = Topics.GROUP_NOTIFICATION)
    public void handlePaymentProcessed(PaymentProcessedEvent event) {
        intake.onPaymentProcessed(event);
    }

    @KafkaListener(topics = Topics.PAYMENT_FAILED, groupId = Topics.GROUP_NOTIFICATION)
    public void handlePaymentFailed(PaymentFailedEvent event) {
        intake.onPaymentFailed(event);
    }
}
