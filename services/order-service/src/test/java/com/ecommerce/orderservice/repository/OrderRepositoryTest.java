package com.ecommerce.orderservice.repository;

import com.ecommerce.common.enums.OrderStatus;
import com.ecommerce.orderservice.Order;
import com.ecommerce.orderservice.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.*;

@DataJpaTest
@ActiveProfiles("test")
@DisplayName("Order Repository Unit Tests")
class OrderRepositoryTest {

    @Autowired
    private OrderRepository orderRepository;

    @BeforeEach
    void setUp() {
        orderRepository.deleteAll();
    }

    @Test
    @DisplayName("Should save and retrieve order successfully")
    void testSaveOrder() {
        Order order = Order.builder()
            .customerId(1L)
            .productId("PROD-001")
            .quantity(5)
            .status(OrderStatus.PENDING)
            .build();

        Order savedOrder = orderRepository.save(order);

        assertThat(savedOrder).isNotNull();
        assertThat(savedOrder.getId()).isNotNull();
        assertThat(savedOrder.getCustomerId()).isEqualTo(1L);
        assertThat(savedOrder.getProductId()).isEqualTo("PROD-001");
        assertThat(savedOrder.getQuantity()).isEqualTo(5);
        assertThat(savedOrder.getStatus()).isEqualTo(OrderStatus.PENDING);
    }

    @Test
    @DisplayName("Should find order by ID")
    void testFindOrderById() {
        Order order = Order.builder()
            .customerId(1L)
            .productId("PROD-001")
            .quantity(5)
            .status(OrderStatus.PENDING)
            .build();
        Order savedOrder = orderRepository.save(order);

        Order foundOrder = orderRepository.findById(savedOrder.getId()).orElse(null);

        assertThat(foundOrder).isNotNull();
        assertThat(foundOrder.getId()).isEqualTo(savedOrder.getId());
        assertThat(foundOrder.getCustomerId()).isEqualTo(1L);
    }

    @Test
    @DisplayName("Should return empty when order not found")
    void testFindOrderByIdNotFound() {
        var result = orderRepository.findById(999L);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("Should update order status successfully")
    void testUpdateOrderStatus() {
        Order order = Order.builder()
            .customerId(1L)
            .productId("PROD-001")
            .quantity(5)
            .status(OrderStatus.PENDING)
            .build();
        Order savedOrder = orderRepository.save(order);

        savedOrder.setStatus(OrderStatus.INVENTORY_RESERVED);
        orderRepository.save(savedOrder);

        Order updatedOrder = orderRepository.findById(savedOrder.getId()).orElse(null);

        assertThat(updatedOrder).isNotNull();
        assertThat(updatedOrder.getStatus()).isEqualTo(OrderStatus.INVENTORY_RESERVED);
    }

    @Test
    @DisplayName("Should delete order successfully")
    void testDeleteOrder() {
        Order order = Order.builder()
            .customerId(1L)
            .productId("PROD-001")
            .quantity(5)
            .status(OrderStatus.PENDING)
            .build();
        Order savedOrder = orderRepository.save(order);

        orderRepository.deleteById(savedOrder.getId());

        var result = orderRepository.findById(savedOrder.getId());

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("Should check if order exists")
    void testExistsById() {
        Order order = Order.builder()
            .customerId(1L)
            .productId("PROD-001")
            .quantity(5)
            .status(OrderStatus.PENDING)
            .build();
        Order savedOrder = orderRepository.save(order);

        assertThat(orderRepository.existsById(savedOrder.getId())).isTrue();
        assertThat(orderRepository.existsById(999L)).isFalse();
    }

    @Test
    @DisplayName("Should count all orders")
    void testCountOrders() {
        for (int i = 1; i <= 5; i++) {
            Order order = Order.builder()
                .customerId((long) i)
                .productId("PROD-" + i)
                .quantity(i)
                .status(OrderStatus.PENDING)
                .build();
            orderRepository.save(order);
        }

        long count = orderRepository.count();

        assertThat(count).isEqualTo(5);
    }

    @Test
    @DisplayName("Should return all orders")
    void testFindAllOrders() {
        for (int i = 1; i <= 3; i++) {
            Order order = Order.builder()
                .customerId((long) i)
                .productId("PROD-" + i)
                .quantity(i)
                .status(OrderStatus.PENDING)
                .build();
            orderRepository.save(order);
        }

        var orders = orderRepository.findAll();

        assertThat(orders).hasSize(3);
    }

    @Test
    @DisplayName("Should persist timestamps on save")
    void testTimestampPersistence() {
        Order order = Order.builder()
            .customerId(1L)
            .productId("PROD-001")
            .quantity(5)
            .status(OrderStatus.PENDING)
            .build();

        Order savedOrder = orderRepository.save(order);

        assertThat(savedOrder.getCreatedAt()).isNotNull();
        assertThat(savedOrder.getUpdatedAt()).isNotNull();
    }

    @Test
    @DisplayName("Should handle order with COMPLETED status")
    void testOrderWithCompletedStatus() {
        Order order = Order.builder()
            .customerId(1L)
            .productId("PROD-001")
            .quantity(5)
            .status(OrderStatus.COMPLETED)
            .build();

        Order savedOrder = orderRepository.save(order);

        assertThat(savedOrder.getStatus()).isEqualTo(OrderStatus.COMPLETED);
    }

    @Test
    @DisplayName("Should handle order with CANCELLED status")
    void testOrderWithCancelledStatus() {
        Order order = Order.builder()
            .customerId(1L)
            .productId("PROD-001")
            .quantity(5)
            .status(OrderStatus.CANCELLED)
            .build();

        Order savedOrder = orderRepository.save(order);

        assertThat(savedOrder.getStatus()).isEqualTo(OrderStatus.CANCELLED);
    }
}
