package com.ecommerce.notificationservice.service;

import com.ecommerce.common.enums.NotificationType;
import com.ecommerce.common.events.OrderCreatedEvent;
import com.ecommerce.common.events.PaymentFailedEvent;
import com.ecommerce.common.events.PaymentProcessedEvent;
import com.ecommerce.common.exception.NonRetryableEventException;
import com.ecommerce.notificationservice.NotificationRepository;
import com.ecommerce.notificationservice.client.CustomerClient;
import com.ecommerce.notificationservice.client.CustomerInfo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * Turns a saga event into a recorded notification. The events are <em>self-contained</em> - each carries the
 * customer id - so the only thing looked up is the customer's email, and no call to the order service is needed.
 *
 * <p>This class is deliberately not transactional: the customer lookup is a remote call and must not hold a
 * database connection. If the lookup fails (service down, too slow, breaker open) the exception propagates and the
 * Kafka error handler retries with backoff, then dead-letters - the notification is never silently dropped. Only
 * the write ({@link NotificationService#record}) is transactional.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationIntake {

    private final NotificationService notificationService;
    private final NotificationRepository notificationRepository;
    private final CustomerClient customers;

    public void onOrderCreated(OrderCreatedEvent event) {
        record(event.getEventId(), event.getCustomerId(), event.getOrderId(), NotificationType.ORDER_CREATED,
                NotificationService.orderCreatedSubject(event.getOrderId()),
                NotificationService.orderCreatedMessage(event.getOrderId(),
                        event.getLines() == null ? 0 : event.getLines().size(), event.getTotalAmount(), event.getCurrency()));
    }

    public void onPaymentProcessed(PaymentProcessedEvent event) {
        record(event.getEventId(), event.getCustomerId(), event.getOrderId(), NotificationType.PAYMENT_SUCCESS,
                NotificationService.paymentSuccessSubject(event.getOrderId()),
                NotificationService.paymentSuccessMessage(event.getOrderId(), event.getAmount(), event.getCurrency()));
    }

    public void onPaymentFailed(PaymentFailedEvent event) {
        record(event.getEventId(), event.getCustomerId(), event.getOrderId(), NotificationType.PAYMENT_FAILED,
                NotificationService.paymentFailedSubject(event.getOrderId()),
                NotificationService.paymentFailedMessage(event.getOrderId(), event.getReason()));
    }

    private void record(String eventId, Long customerId, Long orderId, NotificationType type, String subject, String message) {
        if (customerId == null) {
            throw new NonRetryableEventException("Event " + eventId + " for order " + orderId + " names no customer to notify");
        }
        if (notificationRepository.existsBySourceEventId(eventId)) {
            log.info("Notification for event {} already recorded - skipping", eventId);
            return;
        }
        Optional<CustomerInfo> customer = customers.find(customerId);
        String email = customer.map(CustomerInfo::getEmail).orElse(null);
        notificationService.record(eventId, customerId, orderId, type, subject, message, email);
    }
}
