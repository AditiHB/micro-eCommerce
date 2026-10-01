package com.ecommerce.paymentservice.dto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.*;

@DisplayName("ProcessPaymentRequest DTO Tests")
class ProcessPaymentRequestTest {

    @Test
    @DisplayName("Should create request with valid data")
    void testValidRequest() {
        ProcessPaymentRequest request = ProcessPaymentRequest.builder()
            .orderId(123L)
            .amount(BigDecimal.valueOf(99.99))
            .build();

        assertThat(request.getOrderId()).isEqualTo(123L);
        assertThat(request.getAmount()).isEqualByComparingTo(BigDecimal.valueOf(99.99));
    }

    @Test
    @DisplayName("Should update order ID via setter")
    void testSetOrderId() {
        ProcessPaymentRequest request = new ProcessPaymentRequest();
        request.setOrderId(456L);

        assertThat(request.getOrderId()).isEqualTo(456L);
    }

    @Test
    @DisplayName("Should update amount via setter")
    void testSetAmount() {
        ProcessPaymentRequest request = new ProcessPaymentRequest();
        request.setAmount(BigDecimal.valueOf(49.99));

        assertThat(request.getAmount()).isEqualByComparingTo(BigDecimal.valueOf(49.99));
    }

    @Test
    @DisplayName("Should support all-args constructor")
    void testAllArgsConstructor() {
        ProcessPaymentRequest request = new ProcessPaymentRequest(789L, BigDecimal.valueOf(199.99));

        assertThat(request.getOrderId()).isEqualTo(789L);
        assertThat(request.getAmount()).isEqualByComparingTo(BigDecimal.valueOf(199.99));
    }

    @Test
    @DisplayName("Should support no-args constructor")
    void testNoArgsConstructor() {
        ProcessPaymentRequest request = new ProcessPaymentRequest();

        assertThat(request.getOrderId()).isNull();
        assertThat(request.getAmount()).isNull();
    }

    @Test
    @DisplayName("Should handle zero amount")
    void testZeroAmount() {
        ProcessPaymentRequest request = ProcessPaymentRequest.builder()
            .orderId(0L)
            .amount(BigDecimal.ZERO)
            .build();

        assertThat(request.getAmount()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("Should handle large amount")
    void testLargeAmount() {
        ProcessPaymentRequest request = ProcessPaymentRequest.builder()
            .orderId(999L)
            .amount(BigDecimal.valueOf(9999999.99))
            .build();

        assertThat(request.getAmount()).isEqualByComparingTo(BigDecimal.valueOf(9999999.99));
    }
}
