package com.ecommerce.productservice.event;

import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
public class ProductDeletedEvent {
    private Long productId;
    private String sku;
    private LocalDateTime eventTime;
}
