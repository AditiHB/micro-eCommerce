package com.ecommerce.orderservice;

import com.ecommerce.common.enums.OrderStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

@DisplayName("Order Entity Unit Tests")
class OrderEntityTest {

    @Test
    @DisplayName("Should create order entity")
    void testOrderCreation() {
        Order order = Order.builder()
            .id(1L)
            .customerId(123L)
            .productId("product-456")
            .quantity(5)
            .status(OrderStatus.PENDING)
            .build();

        assertThat(order.getId()).isEqualTo(1L);
        assertThat(order.getCustomerId()).isEqualTo(123L);
        assertThat(order.getProductId()).isEqualTo("product-456");
        assertThat(order.getQuantity()).isEqualTo(5);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);
    }

    @Test
    @DisplayName("Should update order status")
    void testUpdateOrderStatus() {
        Order order = Order.builder()
            .status(OrderStatus.PENDING)
            .build();

        order.setStatus(OrderStatus.COMPLETED);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.COMPLETED);
    }

    @Test
    @DisplayName("Should handle different order statuses")
    void testOrderStatuses() {
        Order order1 = Order.builder().status(OrderStatus.PENDING).build();
        Order order2 = Order.builder().status(OrderStatus.COMPLETED).build();

        assertThat(order1.getStatus()).isNotEqualTo(order2.getStatus());
    }
}
