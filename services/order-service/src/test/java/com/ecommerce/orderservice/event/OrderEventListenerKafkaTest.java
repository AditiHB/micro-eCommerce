package com.ecommerce.orderservice.event;

import com.ecommerce.common.enums.OrderStatus;
import com.ecommerce.common.events.PaymentProcessedEvent;
import com.ecommerce.common.events.InventoryFailedEvent;
import com.ecommerce.common.events.PaymentFailedEvent;
import com.ecommerce.common.events.RefundCompletedEvent;
import com.ecommerce.orderservice.Order;
import com.ecommerce.orderservice.OrderEventListener;
import com.ecommerce.orderservice.OrderRepository;
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
@DisplayName("Order Event Listener Kafka Tests")
class OrderEventListenerKafkaTest {

    @Autowired
    private OrderEventListener orderEventListener;

    @Autowired
    private OrderRepository orderRepository;

    private Order testOrder;

    @BeforeEach
    void setUp() {
        orderRepository.deleteAll();
        testOrder = Order.builder()
            .customerId(1L)
            .productId("PROD-001")
            .quantity(5)
            .status(OrderStatus.PENDING)
            .build();
        testOrder = orderRepository.save(testOrder);
    }

    @Test
    @DisplayName("Should handle PaymentProcessedEvent and update order to COMPLETED")
    void testHandlePaymentProcessedEvent() {
        PaymentProcessedEvent event = new PaymentProcessedEvent(testOrder.getId(), 100.0);

        orderEventListener.handlePaymentProcessed(event, () -> {});

        Order updated = orderRepository.findById(testOrder.getId()).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(OrderStatus.COMPLETED);
    }

    @Test
    @DisplayName("Should handle InventoryFailedEvent and cancel order")
    void testHandleInventoryFailedEvent() {
        InventoryFailedEvent event = new InventoryFailedEvent(testOrder.getId(), "Insufficient stock", "event-1");

        orderEventListener.handleInventoryFailed(event, () -> {});

        Order updated = orderRepository.findById(testOrder.getId()).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(OrderStatus.CANCELLED);
    }

    @Test
    @DisplayName("Should handle PaymentFailedEvent and cancel order")
    void testHandlePaymentFailedEvent() {
        PaymentFailedEvent event = new PaymentFailedEvent(testOrder.getId(), "Insufficient funds", "event-2");

        orderEventListener.handlePaymentFailed(event, () -> {});

        Order updated = orderRepository.findById(testOrder.getId()).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(OrderStatus.CANCELLED);
    }

    @Test
    @DisplayName("Should handle RefundCompletedEvent")
    void testHandleRefundCompletedEvent() {
        Order cancelledOrder = Order.builder()
            .customerId(1L)
            .productId("PROD-001")
            .quantity(5)
            .status(OrderStatus.CANCELLED)
            .build();
        cancelledOrder = orderRepository.save(cancelledOrder);

        RefundCompletedEvent event = new RefundCompletedEvent(cancelledOrder.getId(), 100.0);

        orderEventListener.handleRefundCompleted(event, () -> {});

        Order updated = orderRepository.findById(cancelledOrder.getId()).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(OrderStatus.CANCELLED);
    }

    @Test
    @DisplayName("Should handle multiple sequential payment processed events")
    void testMultiplePaymentProcessedEvents() {
        Order order1 = Order.builder()
            .customerId(1L)
            .productId("PROD-001")
            .quantity(5)
            .status(OrderStatus.PENDING)
            .build();
        order1 = orderRepository.save(order1);

        Order order2 = Order.builder()
            .customerId(2L)
            .productId("PROD-002")
            .quantity(3)
            .status(OrderStatus.PENDING)
            .build();
        order2 = orderRepository.save(order2);

        PaymentProcessedEvent event1 = new PaymentProcessedEvent(order1.getId(), 100.0);
        PaymentProcessedEvent event2 = new PaymentProcessedEvent(order2.getId(), 150.0);

        orderEventListener.handlePaymentProcessed(event1, () -> {});
        orderEventListener.handlePaymentProcessed(event2, () -> {});

        Order updated1 = orderRepository.findById(order1.getId()).orElseThrow();
        Order updated2 = orderRepository.findById(order2.getId()).orElseThrow();

        assertThat(updated1.getStatus()).isEqualTo(OrderStatus.COMPLETED);
        assertThat(updated2.getStatus()).isEqualTo(OrderStatus.COMPLETED);
    }

    @Test
    @DisplayName("Should handle saga pattern: Inventory Failed -> Order Cancelled")
    void testSagaPatternInventoryFailed() {
        testOrder.setStatus(OrderStatus.PENDING);
        orderRepository.save(testOrder);

        InventoryFailedEvent event = new InventoryFailedEvent(testOrder.getId(), "Out of stock", "event-3");

        orderEventListener.handleInventoryFailed(event, () -> {});

        Order cancelled = orderRepository.findById(testOrder.getId()).orElseThrow();
        assertThat(cancelled.getStatus()).isEqualTo(OrderStatus.CANCELLED);
    }

    @Test
    @DisplayName("Should handle saga pattern: Payment Failed -> Order Cancelled")
    void testSagaPatternPaymentFailed() {
        testOrder.setStatus(OrderStatus.PENDING);
        orderRepository.save(testOrder);

        PaymentFailedEvent event = new PaymentFailedEvent(testOrder.getId(), "Payment declined", "event-4");

        orderEventListener.handlePaymentFailed(event, () -> {});

        Order cancelled = orderRepository.findById(testOrder.getId()).orElseThrow();
        assertThat(cancelled.getStatus()).isEqualTo(OrderStatus.CANCELLED);
    }

    @Test
    @DisplayName("Should handle edge case: non-existent order in event")
    void testEventForNonExistentOrder() {
        PaymentProcessedEvent event = new PaymentProcessedEvent(999L, 100.0);

        orderEventListener.handlePaymentProcessed(event, () -> {});

        long count = orderRepository.count();
        assertThat(count).isEqualTo(1);
    }

    @Test
    @DisplayName("Should preserve order data during status transitions")
    void testOrderDataPreservationDuringStatusTransition() {
        PaymentProcessedEvent event = new PaymentProcessedEvent(testOrder.getId(), 100.0);

        orderEventListener.handlePaymentProcessed(event, () -> {});

        Order completed = orderRepository.findById(testOrder.getId()).orElseThrow();
        assertThat(completed.getId()).isEqualTo(testOrder.getId());
        assertThat(completed.getCustomerId()).isEqualTo(testOrder.getCustomerId());
        assertThat(completed.getProductId()).isEqualTo(testOrder.getProductId());
        assertThat(completed.getQuantity()).isEqualTo(testOrder.getQuantity());
        assertThat(completed.getStatus()).isEqualTo(OrderStatus.COMPLETED);
    }
}
