package com.ecommerce.inventoryservice.service;

import com.ecommerce.common.events.EventPublisher;
import com.ecommerce.common.events.InventoryFailedEvent;
import com.ecommerce.common.events.InventoryReleasedEvent;
import com.ecommerce.common.events.InventoryReservedEvent;
import com.ecommerce.common.events.LineItem;
import com.ecommerce.common.events.OrderCancelledEvent;
import com.ecommerce.common.events.OrderCreatedEvent;
import com.ecommerce.common.events.Topics;
import com.ecommerce.common.inbox.InboxService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Inventory's side of the saga. Each handler is one transaction: claim the event in the inbox (a redelivery is
 * skipped), change the stock, and queue the resulting event in the outbox - all committed together or rolled
 * back together and retried, so stock is never taken without the saga hearing about it, nor announced without
 * being taken.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class InventorySagaHandler {

    private static final String CONSUMER = Topics.GROUP_INVENTORY;

    private final ReservationService reservations;
    private final EventPublisher events;
    private final InboxService inbox;

    /** Forward step: hold stock for the new order, then tell the saga whether it worked. */
    public void onOrderCreated(OrderCreatedEvent event) {
        if (!inbox.firstDelivery(CONSUMER, event.getEventId())) {
            return;
        }
        ReservationService.Result result = reservations.reserve(event.getOrderId(), event.getLines());
        if (result.reserved()) {
            events.publish(new InventoryReservedEvent(event.getOrderId(), event.getCustomerId(), event.getLines(),
                    event.getTotalAmount(), event.getCurrency()), null, event.getEventId());
        } else {
            log.warn("Cannot reserve stock for order {}: {}", event.getOrderId(), result.reason());
            events.publish(new InventoryFailedEvent(event.getOrderId(), event.getCustomerId(), result.reason()),
                    null, event.getEventId());
        }
    }

    /** Compensation: the order was cancelled, give its stock back. */
    public void onOrderCancelled(OrderCancelledEvent event) {
        if (!inbox.firstDelivery(CONSUMER, event.getEventId())) {
            return;
        }
        List<LineItem> released = reservations.release(event.getOrderId());
        if (!released.isEmpty()) {
            events.publish(new InventoryReleasedEvent(event.getOrderId(), released), null, event.getEventId());
        }
    }
}
