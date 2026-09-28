package com.ecommerce.paymentservice.dto;

import com.ecommerce.common.enums.PaymentStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.*;

@DisplayName("PaymentResponse DTO Tests")
class PaymentResponseTest {

    @Test
    @DisplayName("Should create response with valid data")
    void testValidResponse() {
        PaymentResponse response = PaymentResponse.builder()
            .id(1L)
            .orderId(123L)
            .amount(BigDecimal.valueOf(99.99))
            .status(PaymentStatus.PROCESSED)
            .build();

        assertThat(response.getId()).isEqualTo(1L);
        assertThat(response.getOrderId()).isEqualTo(123L);
        assertThat(response.getAmount()).isEqualByComparingTo(BigDecimal.valueOf(99.99));
        assertThat(response.getStatus()).isEqualTo(PaymentStatus.PROCESSED);
    }

    @Test
    @DisplayName("Should update order ID via setter")
    void testSetOrderId() {
        PaymentResponse response = new PaymentResponse();
        response.setOrderId(456L);

        assertThat(response.getOrderId()).isEqualTo(456L);
    }

    @Test
    @DisplayName("Should update amount via setter")
    void testSetAmount() {
        PaymentResponse response = new PaymentResponse();
        response.setAmount(BigDecimal.valueOf(49.99));

        assertThat(response.getAmount()).isEqualByComparingTo(BigDecimal.valueOf(49.99));
    }

    @Test
    @DisplayName("Should update status via setter")
    void testSetStatus() {
        PaymentResponse response = new PaymentResponse();
        response.setStatus(PaymentStatus.REFUNDED);

        assertThat(response.getStatus()).isEqualTo(PaymentStatus.REFUNDED);
    }

    @Test
    @DisplayName("Should update ID via setter")
    void testSetId() {
        PaymentResponse response = new PaymentResponse();
        response.setId(5L);

        assertThat(response.getId()).isEqualTo(5L);
    }

    @Test
    @DisplayName("Should support all-args constructor")
    void testAllArgsConstructor() {
        LocalDateTime now = LocalDateTime.now();
        PaymentResponse response = new PaymentResponse(10L, 789L, BigDecimal.valueOf(199.99), PaymentStatus.PROCESSING, now, now);

        assertThat(response.getId()).isEqualTo(10L);
        assertThat(response.getOrderId()).isEqualTo(789L);
        assertThat(response.getAmount()).isEqualByComparingTo(BigDecimal.valueOf(199.99));
        assertThat(response.getStatus()).isEqualTo(PaymentStatus.PROCESSING);
        assertThat(response.getCreatedAt()).isEqualTo(now);
        assertThat(response.getUpdatedAt()).isEqualTo(now);
    }

    @Test
    @DisplayName("Should support no-args constructor")
    void testNoArgsConstructor() {
        PaymentResponse response = new PaymentResponse();

        assertThat(response.getId()).isNull();
        assertThat(response.getOrderId()).isNull();
        assertThat(response.getAmount()).isNull();
        assertThat(response.getStatus()).isNull();
    }

    @Test
    @DisplayName("Should handle zero amount")
    void testZeroAmount() {
        PaymentResponse response = PaymentResponse.builder()
            .id(1L)
            .orderId(0L)
            .amount(BigDecimal.ZERO)
            .status(PaymentStatus.PROCESSED)
            .build();

        assertThat(response.getAmount()).isEqualByComparingTo(BigDecimal.ZERO);
    }
}
