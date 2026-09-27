package com.ecommerce.productservice.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "products")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
@EqualsAndHashCode(of = "id")
public class Product {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "Product name is required")
    @Size(max = 255, message = "Product name must not exceed 255 characters")
    @Column(nullable = false)
    private String name;

    @Column(columnDefinition = "CLOB")
    private String description;

    @NotNull(message = "Product price is required")
    @DecimalMin(value = "0.0", inclusive = false, message = "Product price must be greater than 0")
    @Column(nullable = false)
    private BigDecimal price;

    @NotBlank(message = "SKU is required")
    @Size(max = 100, message = "SKU must not exceed 100 characters")
    @Column(nullable = false, unique = true)
    private String sku;

    @NotBlank(message = "Category is required")
    @Size(max = 100, message = "Category must not exceed 100 characters")
    @Column(nullable = false)
    private String category;

    @NotNull(message = "Quantity available is required")
    @PositiveOrZero(message = "Quantity available must be zero or positive")
    @Column(nullable = false)
    private Integer quantityAvailable;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @Version
    private Long version;
}
