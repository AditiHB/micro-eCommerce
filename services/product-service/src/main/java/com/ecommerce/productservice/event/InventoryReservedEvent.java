package com.ecommerce.productservice.event;

import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
public class InventoryReservedEvent {
    private Long productId;
    private Integer quantityReserved;
    private LocalDateTime eventTime;
}
