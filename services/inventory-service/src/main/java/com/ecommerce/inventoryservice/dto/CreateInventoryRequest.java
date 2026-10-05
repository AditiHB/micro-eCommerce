package com.ecommerce.inventoryservice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateInventoryRequest {
    @NotBlank(message = "Product ID cannot be blank")
    private String productId;

    @NotNull(message = "Quantity cannot be null")
    @PositiveOrZero(message = "Quantity cannot be negative")
    private Integer quantity;
}
