package com.ecommerce.inventoryservice;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * One line of an order's stock reservation: "these units of this product are held for this order". A row exists
 * only while - or after - stock was really taken for the order, so it is also the idempotency guard (one row per
 * order and product, enforced by a unique constraint) and the record that tells a cancellation exactly what to
 * give back. {@link #releasedAt} is set when the units go back to stock; a released line is never released twice.
 */
@Entity
@Table(name = "inventory_reservations")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
@EqualsAndHashCode(of = "id")
public class InventoryReservation {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull(message = "Order ID cannot be null")
    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @NotNull(message = "Product ID cannot be null")
    @Column(name = "product_id", nullable = false)
    private String productId;

    @NotNull(message = "Quantity cannot be null")
    @Column(nullable = false)
    private Integer quantity;

    /** Null while the units are held; set once they were returned to stock. */
    @Column(name = "released_at")
    private LocalDateTime releasedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
