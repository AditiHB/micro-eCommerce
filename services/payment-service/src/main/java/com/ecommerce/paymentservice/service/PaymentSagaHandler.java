package com.ecommerce.paymentservice.service;

import com.ecommerce.common.enums.PaymentStatus;
import com.ecommerce.common.events.InventoryReservedEvent;
import com.ecommerce.common.events.OrderCancelledEvent;
import com.ecommerce.common.events.Topics;
import com.ecommerce.common.exception.NonRetryableEventException;
import com.ecommerce.common.inbox.InboxService;
import com.ecommerce.paymentservice.Payment;
import com.ecommerce.paymentservice.PaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * Payment's side of the saga. Each handler is one transaction: claim the event in the inbox (a redelivery is
 * skipped), move the money through the processor, record the payment, and queue the resulting event in the
 * outbox - committed together, or rolled back and retried together.
 *
 * <p>Two safeguards keep the charge to exactly one per order: a payment row is looked up first, and the unique
 * constraint on {@code order_id} is the backstop that makes a racing duplicate fail (and be retried, finding the
 * payment) instead of charging twice. The charge amount is the order total carried by the event - never a
 * constant, and never supplied by a client.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class PaymentSagaHandler {

    private static final String CONSUMER = Topics.GROUP_PAYMENT;

    private final PaymentRepository paymentRepository;
    private final PaymentService paymentService;
    private final InboxService inbox;

    /** Forward step: stock is held, take the money. A decline becomes {@code payment.failed}. */
    public void onInventoryReserved(InventoryReservedEvent event) {
        if (!inbox.firstDelivery(CONSUMER, event.getEventId())) {
            return;
        }
        BigDecimal amount = event.getTotalAmount();
        if (event.getOrderId() == null || amount == null || amount.signum() <= 0 || event.getCurrency() == null) {
            throw new NonRetryableEventException("inventory.reserved for order " + event.getOrderId()
                    + " carries no chargeable total (" + amount + " " + event.getCurrency() + ")");
        }
        if (paymentRepository.findByOrderId(event.getOrderId()).isPresent()) {
            log.info("Order {} already has a payment - not charging again", event.getOrderId());
            return;
        }
        paymentService.charge(event.getOrderId(), event.getCustomerId(), amount, event.getCurrency());
    }

    /** Compensation: the order was cancelled; if money was taken for it, give it back. */
    public void onOrderCancelled(OrderCancelledEvent event) {
        if (!inbox.firstDelivery(CONSUMER, event.getEventId())) {
            return;
        }
        Optional<Payment> found = paymentRepository.findByOrderId(event.getOrderId());
        if (found.isEmpty()) {
            log.info("Order {} cancelled before any payment was made - nothing to refund", event.getOrderId());
            return;
        }
        Payment payment = found.get();
        if (payment.getStatus() == PaymentStatus.CAPTURED) {
            paymentService.refund(payment);
        } else {
            log.info("Order {} cancelled; its payment is {} so there is nothing to refund", event.getOrderId(), payment.getStatus());
        }
    }
}
