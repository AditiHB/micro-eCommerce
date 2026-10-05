package com.ecommerce.orderservice.service;

import com.ecommerce.common.enums.OrderStatus;
import com.ecommerce.common.events.EventPublisher;
import com.ecommerce.common.events.InventoryFailedEvent;
import com.ecommerce.common.events.InventoryReservedEvent;
import com.ecommerce.common.events.OrderCancelledEvent;
import com.ecommerce.common.events.PaymentFailedEvent;
import com.ecommerce.common.events.PaymentProcessedEvent;
import com.ecommerce.common.events.Topics;
import com.ecommerce.common.inbox.InboxService;
import com.ecommerce.common.testsupport.EventSamples;
import com.ecommerce.orderservice.Order;
import com.ecommerce.orderservice.OrderLine;
import com.ecommerce.orderservice.OrderRepository;
import com.ecommerce.orderservice.idempotency.IdempotencyRecordRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * How the order reacts to each saga event, including the awkward cases: duplicates, late events and events for
 * an order that was cancelled while the saga was still running.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("OrderSagaHandler")
class OrderSagaHandlerTest {

    @Mock
    private OrderRepository orders;
    @Mock
    private IdempotencyRecordRepository idempotency;
    @Mock
    private EventPublisher events;
    @Mock
    private InboxService inbox;

    private OrderSagaHandler handler;

    @BeforeEach
    void setUp() {
        handler = new OrderSagaHandler(orders, new OrderService(orders, idempotency, events), inbox);
    }

    private Order order(OrderStatus status) {
        Order order = Order.place(7L, "USD", List.of(
                OrderLine.builder().productId("SKU-001").quantity(2).unitPrice(new BigDecimal("10.00")).build()));
        order.setId(42L);
        if (status != OrderStatus.PENDING) {
            order.transitionTo(status);
        }
        when(orders.findById(42L)).thenReturn(Optional.of(order));
        return order;
    }

    private void firstDelivery() {
        when(inbox.firstDelivery(eq(Topics.GROUP_ORDER), anyString())).thenReturn(true);
    }

    // ---------------------------------------------------------------- idempotency

    @Test
    @DisplayName("a redelivered event is skipped entirely: no change, no events")
    void duplicateIsIgnored() {
        when(inbox.firstDelivery(eq(Topics.GROUP_ORDER), anyString())).thenReturn(false);

        handler.onPaymentProcessed(EventSamples.paymentProcessed());
        handler.onInventoryReserved(EventSamples.inventoryReserved());
        handler.onInventoryFailed(EventSamples.inventoryFailed());
        handler.onPaymentFailed(EventSamples.paymentFailed());

        verify(orders, never()).findById(any());
        verify(events, never()).publish(any(), any(), any());
    }

    // ---------------------------------------------------------------- forward path

    @Test
    @DisplayName("inventory reserved moves a PENDING order to INVENTORY_RESERVED")
    void reservedMovesForward() {
        firstDelivery();
        Order order = order(OrderStatus.PENDING);

        handler.onInventoryReserved(EventSamples.inventoryReserved());

        assertThat(order.getStatus()).isEqualTo(OrderStatus.INVENTORY_RESERVED);
    }

    @Test
    @DisplayName("payment processed completes the order, also if inventory.reserved has not been seen yet")
    void paymentCompletes() {
        firstDelivery();
        Order reserved = order(OrderStatus.INVENTORY_RESERVED);

        handler.onPaymentProcessed(EventSamples.paymentProcessed());

        assertThat(reserved.getStatus()).isEqualTo(OrderStatus.COMPLETED);
    }

    @Test
    @DisplayName("events arriving out of order still end in COMPLETED: payment first, then the late 'reserved'")
    void outOfOrderEvents() {
        firstDelivery();
        Order order = order(OrderStatus.PENDING);

        handler.onPaymentProcessed(EventSamples.paymentProcessed());
        handler.onInventoryReserved(EventSamples.inventoryReserved());

        assertThat(order.getStatus()).isEqualTo(OrderStatus.COMPLETED);
        verify(events, never()).publish(any(), any(), any());
    }

    // ---------------------------------------------------------------- failure path

    @Test
    @DisplayName("inventory failing cancels the order and announces it, so the other compensations run")
    void inventoryFailureCancels() {
        firstDelivery();
        Order order = order(OrderStatus.PENDING);
        InventoryFailedEvent event = EventSamples.inventoryFailed();

        handler.onInventoryFailed(event);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        ArgumentCaptor<OrderCancelledEvent> published = ArgumentCaptor.forClass(OrderCancelledEvent.class);
        verify(events).publish(published.capture(), any(), eq(event.getEventId()));
        assertThat(published.getValue().getReason()).isEqualTo("Insufficient stock for SKU-001");
    }

    @Test
    @DisplayName("a declined payment cancels the order with the decline reason")
    void paymentFailureCancels() {
        firstDelivery();
        Order order = order(OrderStatus.INVENTORY_RESERVED);
        PaymentFailedEvent event = EventSamples.paymentFailed();

        handler.onPaymentFailed(event);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        ArgumentCaptor<OrderCancelledEvent> published = ArgumentCaptor.forClass(OrderCancelledEvent.class);
        verify(events).publish(published.capture(), any(), eq(event.getEventId()));
        assertThat(published.getValue().getReason()).isEqualTo("Card declined");
    }

    @Test
    @DisplayName("a failure for an order that is already finished changes nothing")
    void failureForFinishedOrderIsIgnored() {
        firstDelivery();
        Order completed = order(OrderStatus.COMPLETED);

        handler.onPaymentFailed(EventSamples.paymentFailed());

        assertThat(completed.getStatus()).isEqualTo(OrderStatus.COMPLETED);
        verify(events, never()).publish(any(), any(), any());
    }

    // ---------------------------------------------------------------- the saga outliving a cancellation

    @Test
    @DisplayName("stock reserved AFTER the order was cancelled: the cancellation is announced again so it is released")
    void lateReservationIsCompensated() {
        firstDelivery();
        Order cancelled = order(OrderStatus.CANCELLED);
        InventoryReservedEvent event = EventSamples.inventoryReserved();

        handler.onInventoryReserved(event);

        assertThat(cancelled.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        verify(events).publish(any(OrderCancelledEvent.class), any(), eq(event.getEventId()));
    }

    @Test
    @DisplayName("money captured AFTER the order was cancelled: the cancellation is announced again so it is refunded")
    void latePaymentIsCompensated() {
        firstDelivery();
        Order cancelled = order(OrderStatus.CANCELLED);
        PaymentProcessedEvent event = EventSamples.paymentProcessed();

        handler.onPaymentProcessed(event);

        assertThat(cancelled.getStatus()).as("a late payment cannot flip it to COMPLETED").isEqualTo(OrderStatus.CANCELLED);
        verify(events).publish(any(OrderCancelledEvent.class), any(), eq(event.getEventId()));
    }

    @Test
    @DisplayName("an event for an order that does not exist is ignored, not an error")
    void unknownOrder() {
        firstDelivery();
        when(orders.findById(42L)).thenReturn(Optional.empty());

        handler.onPaymentProcessed(EventSamples.paymentProcessed());

        verify(events, never()).publish(any(), any(), any());
    }
}
