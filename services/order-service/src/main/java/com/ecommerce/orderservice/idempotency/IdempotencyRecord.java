package com.ecommerce.orderservice.idempotency;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * "This caller already sent this {@code Idempotency-Key}, and it produced this order." Written in the same
 * transaction as the order it points to; the unique (principal, key) constraint is what makes a retry or a
 * duplicate click return the original order instead of creating a second one.
 */
@Entity
@Table(name = "idempotency_keys")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class IdempotencyRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Who sent it: keys are scoped to a caller, so two callers can never collide or read each other's orders. */
    @Column(nullable = false, length = 200)
    private String principal;

    @Column(name = "idempotency_key", nullable = false, length = 200)
    private String idempotencyKey;

    /** Fingerprint of the request, so reusing a key for a <em>different</em> request is an error, not a replay. */
    @Column(name = "request_hash", nullable = false, length = 64)
    private String requestHash;

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
