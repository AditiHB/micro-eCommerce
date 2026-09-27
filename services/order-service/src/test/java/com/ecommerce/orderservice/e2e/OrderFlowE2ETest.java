package com.ecommerce.orderservice.e2e;

import com.ecommerce.common.enums.OrderStatus;
import com.ecommerce.orderservice.Order;
import com.ecommerce.orderservice.OrderRepository;
import com.ecommerce.orderservice.dto.CreateOrderRequest;
import com.ecommerce.orderservice.dto.OrderResponse;
import com.ecommerce.orderservice.service.OrderService;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.AutoConfigureTestEntityManager;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestEntityManager
@Transactional
@EmbeddedKafka(partitions = 1, brokerProperties = {"listeners=PLAINTEXT://localhost:0"})
@ActiveProfiles("test")
@DisplayName("Order Flow End-to-End Tests")
class OrderFlowE2ETest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private OrderService orderService;

    @Autowired
    private OrderRepository orderRepository;

    @BeforeEach
    void setUp() {
        orderRepository.deleteAll();
    }

    @Test
    @DisplayName("Should complete full order creation workflow")
    void testCompleteOrderCreationFlow() {
        CreateOrderRequest request = CreateOrderRequest.builder()
            .customerId(1L)
            .productId("PROD-001")
            .quantity(5)
            .build();

        OrderResponse response = orderService.createOrder(request);

        assertThat(response).isNotNull();
        assertThat(response.getId()).isNotNull();
        assertThat(response.getStatus()).isEqualTo(OrderStatus.PENDING);
        assertThat(response.getCustomerId()).isEqualTo(1L);
        assertThat(response.getProductId()).isEqualTo("PROD-001");
        assertThat(response.getQuantity()).isEqualTo(5);

        Order savedOrder = orderRepository.findById(response.getId()).orElseThrow();
        assertThat(savedOrder).isNotNull();
    }

    @Test
    @DisplayName("Should complete order status transition workflow")
    void testOrderStatusTransitionWorkflow() {
        CreateOrderRequest request = CreateOrderRequest.builder()
            .customerId(1L)
            .productId("PROD-001")
            .quantity(5)
            .build();

        OrderResponse created = orderService.createOrder(request);
        assertThat(created.getStatus()).isEqualTo(OrderStatus.PENDING);

        OrderResponse confirmed = orderService.updateOrderStatus(created.getId(), OrderStatus.INVENTORY_RESERVED);
        assertThat(confirmed.getStatus()).isEqualTo(OrderStatus.INVENTORY_RESERVED);

        OrderResponse completed = orderService.updateOrderStatus(created.getId(), OrderStatus.COMPLETED);
        assertThat(completed.getStatus()).isEqualTo(OrderStatus.COMPLETED);

        Order final_order = orderRepository.findById(created.getId()).orElseThrow();
        assertThat(final_order.getStatus()).isEqualTo(OrderStatus.COMPLETED);
    }

    @Test
    @DisplayName("Should handle multiple concurrent orders")
    void testMultipleConcurrentOrders() {
        CreateOrderRequest[] requests = {
            CreateOrderRequest.builder().customerId(1L).productId("PROD-001").quantity(5).build(),
            CreateOrderRequest.builder().customerId(2L).productId("PROD-002").quantity(3).build(),
            CreateOrderRequest.builder().customerId(3L).productId("PROD-003").quantity(7).build(),
            CreateOrderRequest.builder().customerId(4L).productId("PROD-004").quantity(2).build(),
            CreateOrderRequest.builder().customerId(5L).productId("PROD-005").quantity(10).build()
        };

        OrderResponse[] responses = new OrderResponse[5];
        for (int i = 0; i < requests.length; i++) {
            responses[i] = orderService.createOrder(requests[i]);
            assertThat(responses[i].getStatus()).isEqualTo(OrderStatus.PENDING);
        }

        long totalOrders = orderRepository.count();
        assertThat(totalOrders).isEqualTo(5);

        for (OrderResponse response : responses) {
            Order order = orderRepository.findById(response.getId()).orElseThrow();
            assertThat(order).isNotNull();
        }
    }

    @Test
    @DisplayName("Should retrieve order after creation")
    void testOrderRetrieval() {
        CreateOrderRequest request = CreateOrderRequest.builder()
            .customerId(1L)
            .productId("PROD-001")
            .quantity(5)
            .build();

        OrderResponse created = orderService.createOrder(request);
        OrderResponse retrieved = orderService.getOrder(created.getId());

        assertThat(retrieved).isNotNull();
        assertThat(retrieved.getId()).isEqualTo(created.getId());
        assertThat(retrieved.getCustomerId()).isEqualTo(created.getCustomerId());
        assertThat(retrieved.getProductId()).isEqualTo(created.getProductId());
        assertThat(retrieved.getQuantity()).isEqualTo(created.getQuantity());
    }

    @Test
    @DisplayName("Should handle order pagination in workflow")
    void testOrderPaginationInWorkflow() {
        for (int i = 0; i < 25; i++) {
            CreateOrderRequest request = CreateOrderRequest.builder()
                .customerId((long) (i % 5) + 1)
                .productId("PROD-" + (i % 10))
                .quantity((i % 10) + 1)
                .build();
            orderService.createOrder(request);
        }

        var page1 = orderService.getAllOrders(0, 10, "id");
        var page2 = orderService.getAllOrders(1, 10, "id");
        var page3 = orderService.getAllOrders(2, 10, "id");

        assertThat(page1.getContent()).hasSize(10);
        assertThat(page2.getContent()).hasSize(10);
        assertThat(page3.getContent()).hasSize(5);
        assertThat(page1.getTotalElements()).isEqualTo(25);
    }

    @Test
    @DisplayName("Should maintain data consistency across operations")
    void testDataConsistency() {
        CreateOrderRequest request = CreateOrderRequest.builder()
            .customerId(1L)
            .productId("PROD-001")
            .quantity(5)
            .build();

        OrderResponse created = orderService.createOrder(request);

        orderService.updateOrderStatus(created.getId(), OrderStatus.INVENTORY_RESERVED);
        orderService.updateOrderStatus(created.getId(), OrderStatus.COMPLETED);

        Order final_order = orderRepository.findById(created.getId()).orElseThrow();

        assertThat(final_order.getCustomerId()).isEqualTo(1L);
        assertThat(final_order.getProductId()).isEqualTo("PROD-001");
        assertThat(final_order.getQuantity()).isEqualTo(5);
        assertThat(final_order.getStatus()).isEqualTo(OrderStatus.COMPLETED);
        assertThat(final_order.getCreatedAt()).isNotNull();
        assertThat(final_order.getUpdatedAt()).isNotNull();
    }

    @Test
    @DisplayName("Should handle order creation with various product IDs")
    void testOrderCreationWithVariousProducts() {
        String[] productIds = {"PROD-A", "PROD-B", "PROD-C", "SKU-123", "ITEM-XYZ"};

        for (String productId : productIds) {
            CreateOrderRequest request = CreateOrderRequest.builder()
                .customerId(1L)
                .productId(productId)
                .quantity(1)
                .build();

            OrderResponse response = orderService.createOrder(request);
            assertThat(response.getProductId()).isEqualTo(productId);
        }

        long totalOrders = orderRepository.count();
        assertThat(totalOrders).isEqualTo(5);
    }

    @Test
    @DisplayName("Should handle order creation with various quantities")
    void testOrderCreationWithVariousQuantities() {
        int[] quantities = {1, 10, 100, 1000, 9999};

        for (int qty : quantities) {
            CreateOrderRequest request = CreateOrderRequest.builder()
                .customerId(1L)
                .productId("PROD-001")
                .quantity(qty)
                .build();

            OrderResponse response = orderService.createOrder(request);
            assertThat(response.getQuantity()).isEqualTo(qty);
        }

        long totalOrders = orderRepository.count();
        assertThat(totalOrders).isEqualTo(5);
    }

    @Test
    @DisplayName("Should handle order creation with various customer IDs")
    void testOrderCreationWithVariousCustomers() {
        long[] customerIds = {1L, 100L, 1000L, 99999L, Long.MAX_VALUE - 1};

        for (long customerId : customerIds) {
            CreateOrderRequest request = CreateOrderRequest.builder()
                .customerId(customerId)
                .productId("PROD-001")
                .quantity(1)
                .build();

            OrderResponse response = orderService.createOrder(request);
            assertThat(response.getCustomerId()).isEqualTo(customerId);
        }

        long totalOrders = orderRepository.count();
        assertThat(totalOrders).isEqualTo(5);
    }

    @Test
    @DisplayName("Should handle rapid status transitions")
    void testRapidStatusTransitions() {
        CreateOrderRequest request = CreateOrderRequest.builder()
            .customerId(1L)
            .productId("PROD-001")
            .quantity(5)
            .build();

        OrderResponse created = orderService.createOrder(request);

        for (OrderStatus status : OrderStatus.values()) {
            OrderResponse updated = orderService.updateOrderStatus(created.getId(), status);
            assertThat(updated.getStatus()).isEqualTo(status);
        }

        Order final_order = orderRepository.findById(created.getId()).orElseThrow();
        assertThat(final_order.getStatus()).isEqualTo(OrderStatus.values()[OrderStatus.values().length - 1]);
    }
}
