package com.ecommerce.inventoryservice;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * Idempotency ledger for the saga's Kafka-driven reserve/release path (see
 * InventoryEventListener). A row here means "this order's stock reservation
 * has actually been applied to the inventory table" - its existence (and
 * {@link #releasedAt}) is what makes redelivery of order-created/
 * payment-failed safe to process more than once, the same way
 * payment_service's unique constraint on order_id makes a duplicate payment
 * attempt safe (see V7__Enforce_One_Payment_Per_Order.sql) rather than
 * double-charging - this is that same fix, applied to inventory's
 * decrement/increment instead of payment's insert.
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
    @Column(name = "order_id", nullable = false, unique = true)
    private Long orderId;

    @NotNull(message = "Product ID cannot be null")
    @Column(name = "product_id", nullable = false)
    private String productId;

    @NotNull(message = "Quantity cannot be null")
    @Column(nullable = false)
    private Integer quantity;

    /**
     * Null while the reservation is still active (stock is decremented).
     * Set once {@link InventoryEventListener#handlePaymentFailed} releases
     * it back - a second release for the same order becomes a no-op
     * instead of incrementing the stock twice.
     */
    @Column(name = "released_at")
    private LocalDateTime releasedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
