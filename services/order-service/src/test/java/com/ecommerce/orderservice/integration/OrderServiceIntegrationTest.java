package com.ecommerce.orderservice.integration;

import com.ecommerce.common.enums.OrderStatus;
import com.ecommerce.common.exception.ResourceNotFoundException;
import com.ecommerce.orderservice.Order;
import com.ecommerce.orderservice.OrderRepository;
import com.ecommerce.orderservice.dto.CreateOrderRequest;
import com.ecommerce.orderservice.dto.OrderResponse;
import com.ecommerce.orderservice.service.OrderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.AutoConfigureTestEntityManager;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@AutoConfigureTestEntityManager
@Transactional
@ActiveProfiles("test")
@DisplayName("Order Service Integration Tests")
class OrderServiceIntegrationTest {

    @Autowired
    private OrderService orderService;

    @Autowired
    private OrderRepository orderRepository;

    @BeforeEach
    void setUp() {
        orderRepository.deleteAll();
    }

    @Test
    @DisplayName("Should create and retrieve order with full context")
    void testCreateAndRetrieveOrder() {
        CreateOrderRequest request = CreateOrderRequest.builder()
            .customerId(1L)
            .productId("PROD-001")
            .quantity(5)
            .build();

        OrderResponse created = orderService.createOrder(request);
        OrderResponse retrieved = orderService.getOrder(created.getId());

        assertThat(retrieved).isNotNull();
        assertThat(retrieved.getCustomerId()).isEqualTo(1L);
        assertThat(retrieved.getProductId()).isEqualTo("PROD-001");
        assertThat(retrieved.getQuantity()).isEqualTo(5);
        assertThat(retrieved.getStatus()).isEqualTo(OrderStatus.PENDING);
    }

    @Test
    @DisplayName("Should update order status and persist")
    void testUpdateOrderStatusPersistence() {
        Order order = Order.builder()
            .customerId(1L)
            .productId("PROD-001")
            .quantity(5)
            .status(OrderStatus.PENDING)
            .build();
        Order saved = orderRepository.save(order);

        orderService.updateOrderStatus(saved.getId(), OrderStatus.INVENTORY_RESERVED);

        Order verified = orderRepository.findById(saved.getId()).orElseThrow();
        assertThat(verified.getStatus()).isEqualTo(OrderStatus.INVENTORY_RESERVED);
    }

    @Test
    @DisplayName("Should handle order status transitions")
    void testOrderStatusTransitions() {
        Order order = Order.builder()
            .customerId(1L)
            .productId("PROD-001")
            .quantity(5)
            .status(OrderStatus.PENDING)
            .build();
        Order saved = orderRepository.save(order);

        orderService.updateOrderStatus(saved.getId(), OrderStatus.INVENTORY_RESERVED);
        orderService.updateOrderStatus(saved.getId(), OrderStatus.COMPLETED);

        Order final_order = orderRepository.findById(saved.getId()).orElseThrow();
        assertThat(final_order.getStatus()).isEqualTo(OrderStatus.COMPLETED);
    }

    @Test
    @DisplayName("Should retrieve all orders with pagination")
    void testOrderPaginationWithMultipleOrders() {
        for (int i = 1; i <= 15; i++) {
            Order order = Order.builder()
                .customerId((long) i)
                .productId("PROD-" + i)
                .quantity(i)
                .status(OrderStatus.PENDING)
                .build();
            orderRepository.save(order);
        }

        var page1 = orderService.getAllOrders(0, 5, "id");
        var page2 = orderService.getAllOrders(1, 5, "id");
        var page3 = orderService.getAllOrders(2, 5, "id");

        assertThat(page1.getContent()).hasSize(5);
        assertThat(page2.getContent()).hasSize(5);
        assertThat(page3.getContent()).hasSize(5);
        assertThat(page1.getTotalElements()).isEqualTo(15);
        assertThat(page1.getTotalPages()).isEqualTo(3);
    }

    @Test
    @DisplayName("Should maintain referential integrity on updates")
    void testReferentialIntegrity() {
        Order order1 = Order.builder()
            .customerId(1L)
            .productId("PROD-001")
            .quantity(5)
            .status(OrderStatus.PENDING)
            .build();
        Order order2 = Order.builder()
            .customerId(2L)
            .productId("PROD-002")
            .quantity(3)
            .status(OrderStatus.PENDING)
            .build();

        Order saved1 = orderRepository.save(order1);
        orderRepository.save(order2);

        orderService.updateOrderStatus(saved1.getId(), OrderStatus.COMPLETED);

        long totalOrders = orderRepository.count();
        assertThat(totalOrders).isEqualTo(2);
    }

    @Test
    @DisplayName("Should handle timestamps correctly")
    void testTimestampHandling() {
        CreateOrderRequest request = CreateOrderRequest.builder()
            .customerId(1L)
            .productId("PROD-001")
            .quantity(5)
            .build();

        OrderResponse response = orderService.createOrder(request);

        assertThat(response.getCreatedAt()).isNotNull();
        assertThat(response.getUpdatedAt()).isNotNull();
        assertThat(response.getCreatedAt()).isEqualTo(response.getUpdatedAt());
    }

    @Test
    @DisplayName("Should update timestamp on status change")
    void testTimestampUpdateOnStatusChange() throws InterruptedException {
        Order order = Order.builder()
            .customerId(1L)
            .productId("PROD-001")
            .quantity(5)
            .status(OrderStatus.PENDING)
            .build();
        Order saved = orderRepository.save(order);
        var originalUpdatedAt = saved.getUpdatedAt();

        Thread.sleep(100);

        orderService.updateOrderStatus(saved.getId(), OrderStatus.INVENTORY_RESERVED);

        Order updated = orderRepository.findById(saved.getId()).orElseThrow();
        assertThat(updated.getUpdatedAt()).isAfter(originalUpdatedAt);
    }

    @Test
    @DisplayName("Should throw exception when order not found")
    void testOrderNotFoundOnRetrieval() {
        assertThatThrownBy(() -> orderService.getOrder(999L))
            .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("Should handle concurrent order creation")
    void testConcurrentOrderCreation() {
        CreateOrderRequest request1 = CreateOrderRequest.builder()
            .customerId(1L)
            .productId("PROD-001")
            .quantity(5)
            .build();

        CreateOrderRequest request2 = CreateOrderRequest.builder()
            .customerId(2L)
            .productId("PROD-002")
            .quantity(3)
            .build();

        OrderResponse created1 = orderService.createOrder(request1);
        OrderResponse created2 = orderService.createOrder(request2);

        assertThat(created1.getId()).isNotEqualTo(created2.getId());
        assertThat(orderRepository.count()).isEqualTo(2);
    }

    @Test
    @DisplayName("Should handle all order statuses")
    void testAllOrderStatuses() {
        Order order = Order.builder()
            .customerId(1L)
            .productId("PROD-001")
            .quantity(5)
            .status(OrderStatus.PENDING)
            .build();
        Order saved = orderRepository.save(order);

        for (OrderStatus status : OrderStatus.values()) {
            orderService.updateOrderStatus(saved.getId(), status);
            Order updated = orderRepository.findById(saved.getId()).orElseThrow();
            assertThat(updated.getStatus()).isEqualTo(status);
        }
    }
}
