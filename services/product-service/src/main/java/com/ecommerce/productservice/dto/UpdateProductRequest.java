package com.ecommerce.productservice.dto;

import jakarta.validation.constraints.*;
import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
public class UpdateProductRequest {
    @Size(max = 255, message = "Product name must not exceed 255 characters")
    private String name;

    @Size(max = 5000, message = "Product description must not exceed 5000 characters")
    private String description;

    @DecimalMin(value = "0.01", message = "Product price must be greater than 0")
    private BigDecimal price;

    @Size(max = 100, message = "Category must not exceed 100 characters")
    private String category;

    @Pattern(regexp = "[A-Z]{3}", message = "Currency must be a 3-letter ISO code")
    private String currency;
}
