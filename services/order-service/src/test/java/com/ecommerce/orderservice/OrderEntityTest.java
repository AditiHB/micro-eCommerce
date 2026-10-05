package com.ecommerce.orderservice;

import com.ecommerce.common.enums.OrderStatus;
import com.ecommerce.orderservice.exception.InvalidOrderTransitionException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Order aggregate")
class OrderEntityTest {

    private static OrderLine line(String sku, int quantity, String unitPrice) {
        return OrderLine.builder().productId(sku).quantity(quantity).unitPrice(new BigDecimal(unitPrice)).build();
    }

    private Order order() {
        return Order.place(7L, "USD", List.of(line("SKU-001", 2, "79.99"), line("SKU-002", 1, "12.99")));
    }

    @Test
    @DisplayName("a placed order is PENDING, numbers its lines and computes the total itself")
    void placeComputesTheTotal() {
        Order order = order();

        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);
        assertThat(order.getCustomerId()).isEqualTo(7L);
        assertThat(order.getCurrency()).isEqualTo("USD");
        assertThat(order.getTotalAmount()).isEqualByComparingTo("172.97");
        assertThat(order.getLines()).extracting(OrderLine::getLineNo).containsExactly(1, 2);
        assertThat(order.getLines()).allSatisfy(l -> assertThat(l.getOrder()).isSameAs(order));
    }

    @Test
    @DisplayName("an order follows the saga: reserved, then completed")
    void happyPath() {
        Order order = order();

        order.transitionTo(OrderStatus.INVENTORY_RESERVED);
        order.transitionTo(OrderStatus.COMPLETED);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.COMPLETED);
        assertThat(order.isOpen()).isFalse();
    }

    @Test
    @DisplayName("a late payment cannot flip a cancelled order to COMPLETED")
    void cancelledOrderStaysCancelled() {
        Order order = order();
        order.transitionTo(OrderStatus.CANCELLED);

        assertThatThrownBy(() -> order.transitionTo(OrderStatus.COMPLETED))
                .isInstanceOf(InvalidOrderTransitionException.class)
                .hasMessageContaining("CANCELLED")
                .hasMessageContaining("final");
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
    }

    @Test
    @DisplayName("an order cannot go backwards")
    void noGoingBack() {
        Order order = order();
        order.transitionTo(OrderStatus.INVENTORY_RESERVED);

        assertThatThrownBy(() -> order.transitionTo(OrderStatus.PENDING)).isInstanceOf(InvalidOrderTransitionException.class);
    }

    @Test
    @DisplayName("the status can only be changed through the state machine - there is no setter to bypass it")
    void noStatusSetter() {
        assertThat(Order.class.getMethods()).noneMatch(m -> m.getName().equals("setStatus"));
    }

    @Test
    @DisplayName("a line's total is its unit price times its quantity")
    void lineTotal() {
        assertThat(line("SKU-001", 3, "10.50").getLineTotal()).isEqualByComparingTo("31.50");
    }
}
