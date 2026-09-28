package com.ecommerce.common.events;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.*;

@DisplayName("DomainEvent Unit Tests")
class DomainEventTest {

    @Test
    @DisplayName("Should create OrderCreatedEvent")
    void testOrderCreatedEvent() {
        OrderCreatedEvent event = new OrderCreatedEvent("order-123", "customer-456", "product-789", 5);

        assertThat(event.getOrderId()).isEqualTo("order-123");
        assertThat(event.getCustomerId()).isEqualTo("customer-456");
        assertThat(event.getProductId()).isEqualTo("product-789");
        assertThat(event.getQuantity()).isEqualTo(5);
    }

    @Test
    @DisplayName("Should create OrderCancelledEvent")
    void testOrderCancelledEvent() {
        OrderCancelledEvent event = new OrderCancelledEvent("order-123", "customer-456");

        assertThat(event.getOrderId()).isEqualTo("order-123");
        assertThat(event.getCustomerId()).isEqualTo("customer-456");
    }

    @Test
    @DisplayName("Should create PaymentProcessedEvent")
    void testPaymentProcessedEvent() {
        PaymentProcessedEvent event = new PaymentProcessedEvent(1L, "order-123", java.math.BigDecimal.valueOf(99.99));

        assertThat(event.getPaymentId()).isEqualTo(1L);
        assertThat(event.getOrderId()).isEqualTo("order-123");
    }

    @Test
    @DisplayName("Should create PaymentFailedEvent")
    void testPaymentFailedEvent() {
        PaymentFailedEvent event = new PaymentFailedEvent(1L, "order-123", "Insufficient funds");

        assertThat(event.getPaymentId()).isEqualTo(1L);
        assertThat(event.getOrderId()).isEqualTo("order-123");
    }

    @Test
    @DisplayName("Should create InventoryReservedEvent")
    void testInventoryReservedEvent() {
        InventoryReservedEvent event = new InventoryReservedEvent("product-123", "order-456", 10);

        assertThat(event.getProductId()).isEqualTo("product-123");
        assertThat(event.getOrderId()).isEqualTo("order-456");
        assertThat(event.getQuantity()).isEqualTo(10);
    }

    @Test
    @DisplayName("Should create InventoryReleasedEvent")
    void testInventoryReleasedEvent() {
        InventoryReleasedEvent event = new InventoryReleasedEvent("product-123", "order-456", 10);

        assertThat(event.getProductId()).isEqualTo("product-123");
        assertThat(event.getOrderId()).isEqualTo("order-456");
        assertThat(event.getQuantity()).isEqualTo(10);
    }

    @Test
    @DisplayName("Should create InventoryFailedEvent")
    void testInventoryFailedEvent() {
        InventoryFailedEvent event = new InventoryFailedEvent("product-123", "order-456", "Out of stock");

        assertThat(event.getProductId()).isEqualTo("product-123");
        assertThat(event.getOrderId()).isEqualTo("order-456");
    }

    @Test
    @DisplayName("Should create RefundInitiatedEvent")
    void testRefundInitiatedEvent() {
        RefundInitiatedEvent event = new RefundInitiatedEvent(1L, "order-123", java.math.BigDecimal.valueOf(99.99));

        assertThat(event.getPaymentId()).isEqualTo(1L);
        assertThat(event.getOrderId()).isEqualTo("order-123");
    }

    @Test
    @DisplayName("Should create RefundCompletedEvent")
    void testRefundCompletedEvent() {
        RefundCompletedEvent event = new RefundCompletedEvent(1L, "order-123", java.math.BigDecimal.valueOf(99.99));

        assertThat(event.getPaymentId()).isEqualTo(1L);
        assertThat(event.getOrderId()).isEqualTo("order-123");
    }
}
