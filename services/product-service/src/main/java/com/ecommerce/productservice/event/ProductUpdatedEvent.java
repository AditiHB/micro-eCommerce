package com.ecommerce.productservice.event;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
public class ProductUpdatedEvent {
    private Long productId;
    private String name;
    private BigDecimal price;
    private String category;
    private Integer quantityAvailable;
    private LocalDateTime eventTime;
}
