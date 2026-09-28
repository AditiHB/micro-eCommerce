package com.ecommerce.productservice.event;

import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
public class InventoryReleasedEvent {
    private Long productId;
    private Integer quantityReleased;
    private LocalDateTime eventTime;
}
