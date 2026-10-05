package com.ecommerce.inventoryservice.dto;

import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InventoryResponse {
    private Long id;
    private String productId;
    private Integer quantity;
    /** Version of the stock record; also sent as the ETag. */
    private Long version;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
