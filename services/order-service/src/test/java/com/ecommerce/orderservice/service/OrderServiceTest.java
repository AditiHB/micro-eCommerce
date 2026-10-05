package com.ecommerce.orderservice.service;

import com.ecommerce.common.enums.OrderStatus;
import com.ecommerce.common.events.EventPublisher;
import com.ecommerce.common.events.OrderCancelledEvent;
import com.ecommerce.common.exception.BusinessException;
import com.ecommerce.common.exception.ConflictException;
import com.ecommerce.common.exception.ResourceNotFoundException;
import com.ecommerce.orderservice.Order;
import com.ecommerce.orderservice.OrderLine;
import com.ecommerce.orderservice.OrderRepository;
import com.ecommerce.orderservice.dto.OrderResponse;
import com.ecommerce.orderservice.exception.InvalidOrderTransitionException;
import com.ecommerce.orderservice.idempotency.IdempotencyRecordRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** The rules of OrderService that need no database; persistence behaviour is in OrderServiceIntegrationTest. */
@ExtendWith(MockitoExtension.class)
@DisplayName("OrderService")
class OrderServiceTest {

    @Mock
    private OrderRepository orders;
    @Mock
    private IdempotencyRecordRepository idempotency;
    @Mock
    private EventPublisher events;

    private OrderService service;

    @BeforeEach
    void setUp() {
        service = new OrderService(orders, idempotency, events);
    }

    private Order order(OrderStatus status) {
        Order order = Order.place(7L, "USD", List.of(
                OrderLine.builder().productId("SKU-001").quantity(2).unitPrice(new BigDecimal("10.00")).build()));
        order.setId(5L);
        order.setVersion(0L);
        if (status != OrderStatus.PENDING) {
            order.transitionTo(status);
        }
        return order;
    }

    @Test
    @DisplayName("cancelling is a command: the order moves to CANCELLED and order.cancelled is announced")
    void cancelAnnounces() {
        Order order = order(OrderStatus.INVENTORY_RESERVED);
        when(orders.findById(5L)).thenReturn(Optional.of(order));

        OrderResponse response = service.cancelOrder(5L, "Changed my mind");

        assertThat(response.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        ArgumentCaptor<OrderCancelledEvent> event = ArgumentCaptor.forClass(OrderCancelledEvent.class);
        verify(events).publish(event.capture(), isNull(), isNull());
        assertThat(event.getValue().getOrderId()).isEqualTo(5L);
        assertThat(event.getValue().getCustomerId()).isEqualTo(7L);
        assertThat(event.getValue().getReason()).isEqualTo("Changed my mind");
    }

    @Test
    @DisplayName("cancelling a cancelled order is a harmless no-op: no second event")
    void cancelIsIdempotent() {
        when(orders.findById(5L)).thenReturn(Optional.of(order(OrderStatus.CANCELLED)));

        assertThat(service.cancelOrder(5L, "again").getStatus()).isEqualTo(OrderStatus.CANCELLED);

        verify(events, never()).publish(any(), any(), any());
    }

    @Test
    @DisplayName("a completed order cannot be cancelled (409) and nothing is announced")
    void completedCannotBeCancelled() {
        when(orders.findById(5L)).thenReturn(Optional.of(order(OrderStatus.COMPLETED)));

        assertThatThrownBy(() -> service.cancelOrder(5L, "too late")).isInstanceOf(InvalidOrderTransitionException.class);

        verify(events, never()).publish(any(), any(), any());
    }

    @Test
    @DisplayName("back office cannot complete an order by hand - that would skip payment")
    void cannotCompleteByHand() {
        when(orders.findById(5L)).thenReturn(Optional.of(order(OrderStatus.PENDING)));

        assertThatThrownBy(() -> service.updateOrderStatus(5L, OrderStatus.COMPLETED))
                .isInstanceOf(ConflictException.class)
                .extracting(e -> ((ConflictException) e).getErrorCode()).isEqualTo("ORDER_STATUS_MANAGED_BY_SAGA");
        assertThatThrownBy(() -> service.updateOrderStatus(5L, OrderStatus.INVENTORY_RESERVED))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    @DisplayName("setting CANCELLED by hand runs the cancel command, so the compensations still happen")
    void manualCancelCompensates() {
        when(orders.findById(5L)).thenReturn(Optional.of(order(OrderStatus.PENDING)));

        service.updateOrderStatus(5L, OrderStatus.CANCELLED);

        verify(events).publish(any(OrderCancelledEvent.class), isNull(), isNull());
    }

    @Test
    @DisplayName("an unknown order is a 404")
    void unknownOrder() {
        when(orders.findById(9L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getOrder(9L)).isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> service.cancelOrder(9L, "x")).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("the saga deadline cancels an open order that is stale, and leaves a fresh or finished one alone")
    void expire() {
        Order stale = order(OrderStatus.PENDING);
        stale.setUpdatedAt(LocalDateTime.now().minusMinutes(10));
        Order fresh = order(OrderStatus.PENDING);
        fresh.setUpdatedAt(LocalDateTime.now());
        Order done = order(OrderStatus.COMPLETED);
        done.setUpdatedAt(LocalDateTime.now().minusMinutes(10));
        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(5);
        when(orders.findById(1L)).thenReturn(Optional.of(stale));
        when(orders.findById(2L)).thenReturn(Optional.of(fresh));
        when(orders.findById(3L)).thenReturn(Optional.of(done));

        assertThat(service.expire(1L, cutoff, "timed out")).isTrue();
        assertThat(service.expire(2L, cutoff, "timed out")).isFalse();
        assertThat(service.expire(3L, cutoff, "timed out")).isFalse();

        assertThat(stale.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        verify(events).publish(any(OrderCancelledEvent.class), isNull(), isNull());
    }

    @Test
    @DisplayName("sorting is limited to known fields; anything else is a 400, not a persistence exception")
    void sortAllowList() {
        assertThatThrownBy(() -> service.getAllOrders(0, 20, "customer.password"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode()).isEqualTo("INVALID_SORT_FIELD");
        assertThatThrownBy(() -> service.getOrdersByCustomer(1L, 0, 20, "'; drop table orders; --"))
                .isInstanceOf(BusinessException.class);
        verify(orders, never()).findAll(any(org.springframework.data.domain.Pageable.class));
    }
}
