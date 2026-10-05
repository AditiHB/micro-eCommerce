package com.ecommerce.paymentservice.dto;

import com.ecommerce.common.enums.PaymentStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentResponse {
    private Long id;
    private Long orderId;
    private Long customerId;
    private BigDecimal amount;
    private String currency;
    private PaymentStatus status;
    /** The processor's reference for the last operation; absent until the processor has been called. */
    private String processorReference;
    private String failureReason;
    /** Version of the payment; also sent as the ETag. */
    private Long version;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
