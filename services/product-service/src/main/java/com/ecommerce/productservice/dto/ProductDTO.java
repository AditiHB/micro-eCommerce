package com.ecommerce.productservice.dto;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
public class ProductDTO {
    private Long id;
    private String name;
    private String description;
    private BigDecimal price;
    private String sku;
    private String category;
    private String currency;
    /** Version of the entry; also sent as the ETag. */
    private Long version;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
