package com.ecommerce.customerservice.dto;

import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CustomerResponse {
    private Long id;
    private String name;
    private String email;
    /** Version of the customer record; also sent as the ETag. */
    private Long version;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    /** Last 4 digits of the card on file, or null if none - never the encrypted card number itself. */
    private String cardLastFour;
}
