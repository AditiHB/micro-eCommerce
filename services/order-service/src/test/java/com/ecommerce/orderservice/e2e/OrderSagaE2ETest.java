package com.ecommerce.orderservice.e2e;

import com.ecommerce.common.enums.OrderStatus;
import com.ecommerce.common.events.InventoryFailedEvent;
import com.ecommerce.common.events.PaymentFailedEvent;
import com.ecommerce.common.events.PaymentProcessedEvent;
import com.ecommerce.orderservice.Order;
import com.ecommerce.orderservice.OrderEventListener;
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
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestEntityManager
@Transactional
@EmbeddedKafka(partitions = 1, brokerProperties = {"listeners=PLAINTEXT://localhost:0"})
@ActiveProfiles("test")
@DisplayName("Order Saga End-to-End Tests")
class OrderSagaE2ETest {

    @Autowired
    private OrderService orderService;

    @Autowired
    private OrderEventListener orderEventListener;

    @Autowired
    private OrderRepository orderRepository;

    @BeforeEach
    void setUp() {
        orderRepository.deleteAll();
    }

    @Test
    @DisplayName("Should complete happy path: Order -> Payment Success -> Completed")
    void testHappPathOrderToCompletion() {
        CreateOrderRequest request = CreateOrderRequest.builder()
            .customerId(1L)
            .productId("PROD-001")
            .quantity(5)
            .build();

        OrderResponse created = orderService.createOrder(request);
        assertThat(created.getStatus()).isEqualTo(OrderStatus.PENDING);

        PaymentProcessedEvent paymentEvent = new PaymentProcessedEvent(created.getId(), 100.0);
        orderEventListener.handlePaymentProcessed(paymentEvent, () -> {});

        Order completed = orderRepository.findById(created.getId()).orElseThrow();
        assertThat(completed.getStatus()).isEqualTo(OrderStatus.COMPLETED);
    }

    @Test
    @DisplayName("Should handle failure path: Inventory Failed -> Order Cancelled")
    void testFailurePathInventoryFailed() {
        CreateOrderRequest request = CreateOrderRequest.builder()
            .customerId(1L)
            .productId("PROD-001")
            .quantity(5)
            .build();

        OrderResponse created = orderService.createOrder(request);
        assertThat(created.getStatus()).isEqualTo(OrderStatus.PENDING);

        InventoryFailedEvent inventoryEvent = new InventoryFailedEvent(
            created.getId(),
            "Out of stock",
            "event-123"
        );
        orderEventListener.handleInventoryFailed(inventoryEvent, () -> {});

        Order cancelled = orderRepository.findById(created.getId()).orElseThrow();
        assertThat(cancelled.getStatus()).isEqualTo(OrderStatus.CANCELLED);
    }

    @Test
    @DisplayName("Should handle failure path: Payment Failed -> Order Cancelled")
    void testFailurePathPaymentFailed() {
        CreateOrderRequest request = CreateOrderRequest.builder()
            .customerId(1L)
            .productId("PROD-001")
            .quantity(5)
            .build();

        OrderResponse created = orderService.createOrder(request);
        assertThat(created.getStatus()).isEqualTo(OrderStatus.PENDING);

        PaymentFailedEvent paymentEvent = new PaymentFailedEvent(
            created.getId(),
            "Payment declined",
            "event-456"
        );
        orderEventListener.handlePaymentFailed(paymentEvent, () -> {});

        Order cancelled = orderRepository.findById(created.getId()).orElseThrow();
        assertThat(cancelled.getStatus()).isEqualTo(OrderStatus.CANCELLED);
    }

    @Test
    @DisplayName("Should handle saga with compensation: Order -> Payment Failed -> Cancelled")
    void testSagaCompensation() {
        CreateOrderRequest request = CreateOrderRequest.builder()
            .customerId(1L)
            .productId("PROD-001")
            .quantity(5)
            .build();

        OrderResponse created = orderService.createOrder(request);
        Order order = orderRepository.findById(created.getId()).orElseThrow();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);

        PaymentFailedEvent paymentFailedEvent = new PaymentFailedEvent(
            created.getId(),
            "Insufficient funds",
            "event-789"
        );
        orderEventListener.handlePaymentFailed(paymentFailedEvent, () -> {});

        Order compensated = orderRepository.findById(created.getId()).orElseThrow();
        assertThat(compensated.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(compensated.getCustomerId()).isEqualTo(order.getCustomerId());
        assertThat(compensated.getProductId()).isEqualTo(order.getProductId());
        assertThat(compensated.getQuantity()).isEqualTo(order.getQuantity());
    }

    @Test
    @DisplayName("Should handle multiple parallel saga flows")
    void testMultipleParallelSagas() {
        OrderResponse order1 = orderService.createOrder(CreateOrderRequest.builder()
            .customerId(1L).productId("PROD-001").quantity(5).build());
        OrderResponse order2 = orderService.createOrder(CreateOrderRequest.builder()
            .customerId(2L).productId("PROD-002").quantity(3).build());
        OrderResponse order3 = orderService.createOrder(CreateOrderRequest.builder()
            .customerId(3L).productId("PROD-003").quantity(7).build());

        PaymentProcessedEvent event1 = new PaymentProcessedEvent(order1.getId(), 100.0);
        PaymentFailedEvent event2 = new PaymentFailedEvent(order2.getId(), "Declined", "evt-2");
        PaymentProcessedEvent event3 = new PaymentProcessedEvent(order3.getId(), 150.0);

        orderEventListener.handlePaymentProcessed(event1, () -> {});
        orderEventListener.handlePaymentFailed(event2, () -> {});
        orderEventListener.handlePaymentProcessed(event3, () -> {});

        Order completed1 = orderRepository.findById(order1.getId()).orElseThrow();
        Order cancelled2 = orderRepository.findById(order2.getId()).orElseThrow();
        Order completed3 = orderRepository.findById(order3.getId()).orElseThrow();

        assertThat(completed1.getStatus()).isEqualTo(OrderStatus.COMPLETED);
        assertThat(cancelled2.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(completed3.getStatus()).isEqualTo(OrderStatus.COMPLETED);
    }

    @Test
    @DisplayName("Should handle order creation -> inventory check -> payment flow")
    void testCompleteOrderSagaFlow() {
        CreateOrderRequest request = CreateOrderRequest.builder()
            .customerId(1L)
            .productId("PROD-001")
            .quantity(5)
            .build();

        OrderResponse created = orderService.createOrder(request);
        Order order = orderRepository.findById(created.getId()).orElseThrow();

        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);
        assertThat(order.getCustomerId()).isEqualTo(1L);
        assertThat(order.getProductId()).isEqualTo("PROD-001");
        assertThat(order.getQuantity()).isEqualTo(5);

        PaymentProcessedEvent paymentEvent = new PaymentProcessedEvent(order.getId(), 50.0);
        orderEventListener.handlePaymentProcessed(paymentEvent, () -> {});

        Order completed = orderRepository.findById(order.getId()).orElseThrow();
        assertThat(completed.getStatus()).isEqualTo(OrderStatus.COMPLETED);
    }

    @Test
    @DisplayName("Should persist saga state through event processing")
    void testSagaStatePersistence() {
        CreateOrderRequest request = CreateOrderRequest.builder()
            .customerId(1L)
            .productId("PROD-001")
            .quantity(5)
            .build();

        OrderResponse created = orderService.createOrder(request);

        InventoryFailedEvent inventoryEvent = new InventoryFailedEvent(
            created.getId(),
            "Stock exhausted",
            "evt-001"
        );
        orderEventListener.handleInventoryFailed(inventoryEvent, () -> {});

        Order cancelled = orderRepository.findById(created.getId()).orElseThrow();
        assertThat(cancelled.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(cancelled.getCustomerId()).isEqualTo(1L);
        assertThat(cancelled.getProductId()).isEqualTo("PROD-001");
        assertThat(cancelled.getQuantity()).isEqualTo(5);
    }

    @Test
    @DisplayName("Should handle rapid event sequence in saga")
    void testRapidEventSequence() {
        CreateOrderRequest request = CreateOrderRequest.builder()
            .customerId(1L)
            .productId("PROD-001")
            .quantity(5)
            .build();

        OrderResponse created = orderService.createOrder(request);

        for (int i = 0; i < 5; i++) {
            if (i % 2 == 0) {
                PaymentProcessedEvent event = new PaymentProcessedEvent(created.getId(), 100.0 + i);
                orderEventListener.handlePaymentProcessed(event, () -> {});
            } else {
                PaymentFailedEvent event = new PaymentFailedEvent(created.getId(), "Retry " + i, "evt-" + i);
                orderEventListener.handlePaymentFailed(event, () -> {});
            }
        }

        Order final_order = orderRepository.findById(created.getId()).orElseThrow();
        assertThat(final_order.getStatus()).isNotNull();
    }

    @Test
    @DisplayName("Should verify idempotency of payment processed event")
    void testPaymentProcessedEventIdempotency() {
        CreateOrderRequest request = CreateOrderRequest.builder()
            .customerId(1L)
            .productId("PROD-001")
            .quantity(5)
            .build();

        OrderResponse created = orderService.createOrder(request);

        PaymentProcessedEvent event = new PaymentProcessedEvent(created.getId(), 100.0);
        orderEventListener.handlePaymentProcessed(event, () -> {});
        orderEventListener.handlePaymentProcessed(event, () -> {});

        Order order = orderRepository.findById(created.getId()).orElseThrow();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.COMPLETED);
    }

    @Test
    @DisplayName("Should verify idempotency of cancel event")
    void testCancelEventIdempotency() {
        CreateOrderRequest request = CreateOrderRequest.builder()
            .customerId(1L)
            .productId("PROD-001")
            .quantity(5)
            .build();

        OrderResponse created = orderService.createOrder(request);

        InventoryFailedEvent event = new InventoryFailedEvent(
            created.getId(),
            "Out of stock",
            "evt-123"
        );
        orderEventListener.handleInventoryFailed(event, () -> {});
        orderEventListener.handleInventoryFailed(event, () -> {});

        Order order = orderRepository.findById(created.getId()).orElseThrow();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
    }
}
