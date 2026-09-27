package com.ecommerce.paymentservice;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

/**
 * Payment Repository for accessing Payment entities.
 * Supports query methods for refund processing and compensation.
 */
public interface PaymentRepository extends JpaRepository<Payment, Long> {
    /**
     * Find payment by order ID - used for refund processing (compensating transactions).
     */
    Optional<Payment> findByOrderId(Long orderId);
}
