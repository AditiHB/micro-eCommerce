package com.ecommerce.paymentservice.dto;

import com.ecommerce.common.config.JacksonConfig;
import com.ecommerce.common.enums.PaymentStatus;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("PaymentResponse wire format")
class PaymentResponseTest {

    @Test
    @DisplayName("shows the amount in its currency, the lifecycle status, the processor reference and the ETag version")
    void wireFormat() {
        PaymentResponse response = PaymentResponse.builder().id(1L).orderId(42L).customerId(7L)
                .amount(new BigDecimal("172.97")).currency("USD").status(PaymentStatus.CAPTURED)
                .processorReference("sim_cap_1").version(2L).build();

        JsonNode json = JacksonConfig.newObjectMapper().valueToTree(response);

        assertThat(json.get("status").asText()).isEqualTo("CAPTURED");
        assertThat(json.get("currency").asText()).isEqualTo("USD");
        assertThat(json.get("amount").decimalValue()).isEqualByComparingTo("172.97");
        assertThat(json.get("processorReference").asText()).isEqualTo("sim_cap_1");
        assertThat(json.get("version").asLong()).isEqualTo(2L);
    }
}
