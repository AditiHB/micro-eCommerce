package com.ecommerce.orderservice.service;

import com.ecommerce.common.enums.OrderStatus;
import com.ecommerce.common.events.InventoryFailedEvent;
import com.ecommerce.common.events.InventoryReservedEvent;
import com.ecommerce.common.events.PaymentFailedEvent;
import com.ecommerce.common.events.PaymentProcessedEvent;
import com.ecommerce.common.events.RefundCompletedEvent;
import com.ecommerce.common.events.Topics;
import com.ecommerce.common.inbox.InboxService;
import com.ecommerce.orderservice.Order;
import com.ecommerce.orderservice.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * The order's side of the saga. Every handler is one database transaction that
 * <ol>
 *   <li>claims the event in the inbox, so a redelivery is skipped instead of applied twice;</li>
 *   <li>applies the change through the order's state machine; and</li>
 *   <li>queues whatever events follow, in the outbox, atomically with that change.</li>
 * </ol>
 * A failure anywhere rolls all three back and is retried by the Kafka error handler - nothing is half-applied.
 *
 * <p>Events can arrive late or out of order (they travel on different topics), so the handlers never assume the
 * order is still where the event expects it. The one case that matters is an event that shows the saga kept going
 * after the order was cancelled - stock reserved or money taken for a dead order: the cancellation is announced
 * again, and the idempotent compensations (release the reservation, refund the payment) run.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class OrderSagaHandler {

    private static final String CONSUMER = Topics.GROUP_ORDER;

    private final OrderRepository orderRepository;
    private final OrderService orderService;
    private final InboxService inbox;

    public void onInventoryReserved(InventoryReservedEvent event) {
        if (!inbox.firstDelivery(CONSUMER, event.getEventId())) {
            return;
        }
        order(event.getOrderId()).ifPresent(order -> {
            switch (order.getStatus()) {
                case PENDING -> order.transitionTo(OrderStatus.INVENTORY_RESERVED);
                case CANCELLED -> {
                    log.warn("Stock was reserved for order {} after it was cancelled - asking inventory to release it", order.getId());
                    orderService.announceCancellation(order, "Stock reserved after the order was cancelled", event.getEventId());
                }
                default -> log.debug("Order {} is already {}; inventory-reserved changes nothing", order.getId(), order.getStatus());
            }
        });
    }

    public void onInventoryFailed(InventoryFailedEvent event) {
        if (!inbox.firstDelivery(CONSUMER, event.getEventId())) {
            return;
        }
        order(event.getOrderId()).ifPresent(order -> cancelIfPossible(order,
                event.getReason() != null ? event.getReason() : "Inventory reservation failed", event.getEventId()));
    }

    public void onPaymentProcessed(PaymentProcessedEvent event) {
        if (!inbox.firstDelivery(CONSUMER, event.getEventId())) {
            return;
        }
        order(event.getOrderId()).ifPresent(order -> {
            switch (order.getStatus()) {
                case PENDING, INVENTORY_RESERVED, PAYMENT_PROCESSING -> {
                    order.transitionTo(OrderStatus.COMPLETED);
                    log.info("Order {} COMPLETED - payment {} captured ({} {})",
                            order.getId(), event.getPaymentId(), event.getAmount(), event.getCurrency());
                }
                case CANCELLED -> {
                    log.warn("Payment {} was captured for order {} after it was cancelled - asking payment to refund it",
                            event.getPaymentId(), order.getId());
                    orderService.announceCancellation(order, "Payment captured after the order was cancelled", event.getEventId());
                }
                default -> log.debug("Order {} is already {}; payment-processed changes nothing", order.getId(), order.getStatus());
            }
        });
    }

    public void onPaymentFailed(PaymentFailedEvent event) {
        if (!inbox.firstDelivery(CONSUMER, event.getEventId())) {
            return;
        }
        order(event.getOrderId()).ifPresent(order -> cancelIfPossible(order,
                event.getReason() != null ? event.getReason() : "Payment failed", event.getEventId()));
    }

    public void onRefundCompleted(RefundCompletedEvent event) {
        if (!inbox.firstDelivery(CONSUMER, event.getEventId())) {
            return;
        }
        log.info("Saga compensated for order {}: payment {} refunded ({} {})",
                event.getOrderId(), event.getPaymentId(), event.getRefundAmount(), event.getCurrency());
    }

    private Optional<Order> order(Long orderId) {
        Optional<Order> found = orderRepository.findById(orderId);
        if (found.isEmpty()) {
            log.warn("Saga event for order {} which does not exist - ignoring", orderId);
        }
        return found;
    }

    /** Cancels an order that can still be cancelled; one already cancelled or finished is left alone. */
    private void cancelIfPossible(Order order, String reason, String causationId) {
        if (order.getStatus().canTransitionTo(OrderStatus.CANCELLED)) {
            orderService.cancel(order, reason, causationId);
        } else {
            log.info("Order {} is already {}; not cancelling it for: {}", order.getId(), order.getStatus(), reason);
        }
    }
}
