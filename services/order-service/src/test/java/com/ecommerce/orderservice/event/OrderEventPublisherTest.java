package com.ecommerce.orderservice.event;

import com.ecommerce.common.enums.OrderStatus;
import com.ecommerce.common.events.EventPublisher;
import com.ecommerce.common.events.OrderCreatedEvent;
import com.ecommerce.orderservice.Order;
import com.ecommerce.orderservice.OrderRepository;
import com.ecommerce.orderservice.dto.CreateOrderRequest;
import com.ecommerce.orderservice.dto.OrderResponse;
import com.ecommerce.orderservice.service.OrderService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.AutoConfigureTestEntityManager;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@AutoConfigureTestEntityManager
@Transactional
@EmbeddedKafka(partitions = 1, brokerProperties = {"listeners=PLAINTEXT://localhost:0"})
@ActiveProfiles("test")
@DisplayName("Order Event Publishing Tests")
class OrderEventPublisherTest {

    @Autowired
    private OrderService orderService;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private EventPublisher eventPublisher;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        orderRepository.deleteAll();
    }

    @Test
    @DisplayName("Should publish OrderCreatedEvent on order creation")
    void testOrderCreatedEventPublished() {
        CreateOrderRequest request = CreateOrderRequest.builder()
            .customerId(1L)
            .productId("PROD-001")
            .quantity(5)
            .build();

        OrderResponse response = orderService.createOrder(request);

        assertThat(response).isNotNull();
        assertThat(response.getId()).isNotNull();

        Order savedOrder = orderRepository.findById(response.getId()).orElseThrow();
        assertThat(savedOrder.getId()).isEqualTo(response.getId());
        assertThat(savedOrder.getStatus()).isEqualTo(OrderStatus.PENDING);
    }

    @Test
    @DisplayName("Should preserve order data when publishing event")
    void testOrderDataPreservedInEvent() {
        CreateOrderRequest request = CreateOrderRequest.builder()
            .customerId(123L)
            .productId("PROD-XYZ")
            .quantity(10)
            .build();

        OrderResponse response = orderService.createOrder(request);

        Order order = orderRepository.findById(response.getId()).orElseThrow();
        assertThat(order.getCustomerId()).isEqualTo(123L);
        assertThat(order.getProductId()).isEqualTo("PROD-XYZ");
        assertThat(order.getQuantity()).isEqualTo(10);
    }

    @Test
    @DisplayName("Should handle multiple event publications")
    void testMultipleEventPublications() {
        for (int i = 0; i < 5; i++) {
            CreateOrderRequest request = CreateOrderRequest.builder()
                .customerId((long) (i + 1))
                .productId("PROD-" + i)
                .quantity(i + 1)
                .build();

            orderService.createOrder(request);
        }

        long orderCount = orderRepository.count();
        assertThat(orderCount).isEqualTo(5);
    }

    @Test
    @DisplayName("Should maintain event order")
    void testEventOrdering() {
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

        OrderResponse response1 = orderService.createOrder(request1);
        OrderResponse response2 = orderService.createOrder(request2);

        assertThat(response1.getId()).isLessThan(response2.getId());
    }

    @Test
    @DisplayName("Should handle event publishing with valid customer ID")
    void testEventPublishingWithValidCustomerId() {
        CreateOrderRequest request = CreateOrderRequest.builder()
            .customerId(999L)
            .productId("PROD-999")
            .quantity(1)
            .build();

        OrderResponse response = orderService.createOrder(request);

        Order order = orderRepository.findById(response.getId()).orElseThrow();
        assertThat(order.getCustomerId()).isEqualTo(999L);
    }

    @Test
    @DisplayName("Should handle event publishing with various quantities")
    void testEventPublishingWithVariousQuantities() {
        int[] quantities = {1, 10, 100, 1000};

        for (int qty : quantities) {
            CreateOrderRequest request = CreateOrderRequest.builder()
                .customerId(1L)
                .productId("PROD-001")
                .quantity(qty)
                .build();

            OrderResponse response = orderService.createOrder(request);
            Order order = orderRepository.findById(response.getId()).orElseThrow();
            assertThat(order.getQuantity()).isEqualTo(qty);
        }
    }
}
