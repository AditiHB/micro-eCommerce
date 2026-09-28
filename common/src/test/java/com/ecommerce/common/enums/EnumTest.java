package com.ecommerce.common.enums;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

@DisplayName("Enum Classes Unit Tests")
class EnumTest {

    @Test
    @DisplayName("Should have OrderStatus values")
    void testOrderStatusValues() {
        assertThat(OrderStatus.values()).isNotEmpty();
        assertThat(OrderStatus.valueOf("PENDING")).isNotNull();
    }

    @Test
    @DisplayName("Should have PaymentStatus values")
    void testPaymentStatusValues() {
        assertThat(PaymentStatus.values()).isNotEmpty();
        assertThat(PaymentStatus.valueOf("PENDING")).isNotNull();
    }

    @Test
    @DisplayName("Should convert OrderStatus to string")
    void testOrderStatusToString() {
        OrderStatus status = OrderStatus.PENDING;
        assertThat(status.toString()).isNotNull();
    }

    @Test
    @DisplayName("Should convert PaymentStatus to string")
    void testPaymentStatusToString() {
        PaymentStatus status = PaymentStatus.PENDING;
        assertThat(status.toString()).isNotNull();
    }

    @Test
    @DisplayName("Should compare OrderStatus values")
    void testOrderStatusComparison() {
        OrderStatus status1 = OrderStatus.PENDING;
        OrderStatus status2 = OrderStatus.PENDING;

        assertThat(status1).isEqualTo(status2);
    }

    @Test
    @DisplayName("Should compare PaymentStatus values")
    void testPaymentStatusComparison() {
        PaymentStatus status1 = PaymentStatus.PROCESSED;
        PaymentStatus status2 = PaymentStatus.PROCESSED;

        assertThat(status1).isEqualTo(status2);
    }

    @Test
    @DisplayName("Should differentiate between different OrderStatus")
    void testDifferentOrderStatus() {
        OrderStatus pending = OrderStatus.PENDING;
        OrderStatus completed = OrderStatus.COMPLETED;

        assertThat(pending).isNotEqualTo(completed);
    }

    @Test
    @DisplayName("Should differentiate between different PaymentStatus")
    void testDifferentPaymentStatus() {
        PaymentStatus pending = PaymentStatus.PENDING;
        PaymentStatus failed = PaymentStatus.FAILED;

        assertThat(pending).isNotEqualTo(failed);
    }

    @Test
    @DisplayName("Should be able to iterate OrderStatus")
    void testIterateOrderStatus() {
        OrderStatus[] statuses = OrderStatus.values();
        assertThat(statuses).isNotEmpty();
        for (OrderStatus status : statuses) {
            assertThat(status).isNotNull();
        }
    }

    @Test
    @DisplayName("Should be able to iterate PaymentStatus")
    void testIteratePaymentStatus() {
        PaymentStatus[] statuses = PaymentStatus.values();
        assertThat(statuses).isNotEmpty();
        for (PaymentStatus status : statuses) {
            assertThat(status).isNotNull();
        }
    }
}
