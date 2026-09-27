package com.ecommerce.paymentservice.dto;

import com.ecommerce.common.enums.PaymentStatus;
import lombok.*;

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
    private BigDecimal amount;
    private PaymentStatus status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
