package com.ecommerce.common.events;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.*;

@DisplayName("DomainEvent Unit Tests")
class DomainEventTest {

    @Test
    @DisplayName("Should create OrderCreatedEvent")
    void testOrderCreatedEvent() {
        OrderCreatedEvent event = new OrderCreatedEvent(123L, 456L, "product-789", 5);

        assertThat(event.getOrderId()).isEqualTo(123L);
        assertThat(event.getCustomerId()).isEqualTo(456L);
        assertThat(event.getProductId()).isEqualTo("product-789");
        assertThat(event.getQuantity()).isEqualTo(5);
    }

    @Test
    @DisplayName("Should create OrderCancelledEvent")
    void testOrderCancelledEvent() {
        OrderCancelledEvent event = new OrderCancelledEvent(123L, "Customer requested cancellation");

        assertThat(event.getOrderId()).isEqualTo(123L);
        assertThat(event.getReason()).isEqualTo("Customer requested cancellation");
    }

    @Test
    @DisplayName("Should create PaymentProcessedEvent")
    void testPaymentProcessedEvent() {
        PaymentProcessedEvent event = new PaymentProcessedEvent(1L, 123L, BigDecimal.valueOf(99.99));

        assertThat(event.getPaymentId()).isEqualTo(1L);
        assertThat(event.getOrderId()).isEqualTo(123L);
        assertThat(event.getAmount()).isEqualTo(BigDecimal.valueOf(99.99));
    }

    @Test
    @DisplayName("Should create PaymentFailedEvent")
    void testPaymentFailedEvent() {
        PaymentFailedEvent event = new PaymentFailedEvent(123L, "product-789", 5, "Insufficient funds");

        assertThat(event.getOrderId()).isEqualTo(123L);
        assertThat(event.getProductId()).isEqualTo("product-789");
        assertThat(event.getQuantity()).isEqualTo(5);
        assertThat(event.getReason()).isEqualTo("Insufficient funds");
    }

    @Test
    @DisplayName("Should create InventoryReservedEvent")
    void testInventoryReservedEvent() {
        InventoryReservedEvent event = new InventoryReservedEvent(456L, "product-123", 10);

        assertThat(event.getOrderId()).isEqualTo(456L);
        assertThat(event.getProductId()).isEqualTo("product-123");
        assertThat(event.getQuantity()).isEqualTo(10);
    }

    @Test
    @DisplayName("Should create InventoryReleasedEvent")
    void testInventoryReleasedEvent() {
        InventoryReleasedEvent event = new InventoryReleasedEvent(456L, "product-123", 10);

        assertThat(event.getOrderId()).isEqualTo(456L);
        assertThat(event.getProductId()).isEqualTo("product-123");
        assertThat(event.getQuantity()).isEqualTo(10);
    }

    @Test
    @DisplayName("Should create InventoryFailedEvent")
    void testInventoryFailedEvent() {
        InventoryFailedEvent event = new InventoryFailedEvent(456L);

        assertThat(event.getOrderId()).isEqualTo(456L);
    }

    @Test
    @DisplayName("Should create RefundInitiatedEvent")
    void testRefundInitiatedEvent() {
        RefundInitiatedEvent event = new RefundInitiatedEvent(123L, 1L, BigDecimal.valueOf(99.99), "Order cancelled");

        assertThat(event.getPaymentId()).isEqualTo(1L);
        assertThat(event.getOrderId()).isEqualTo(123L);
        assertThat(event.getRefundAmount()).isEqualTo(BigDecimal.valueOf(99.99));
    }

    @Test
    @DisplayName("Should create RefundCompletedEvent")
    void testRefundCompletedEvent() {
        RefundCompletedEvent event = new RefundCompletedEvent(123L, 1L, BigDecimal.valueOf(99.99));

        assertThat(event.getPaymentId()).isEqualTo(1L);
        assertThat(event.getOrderId()).isEqualTo(123L);
        assertThat(event.getRefundAmount()).isEqualTo(BigDecimal.valueOf(99.99));
    }
}
