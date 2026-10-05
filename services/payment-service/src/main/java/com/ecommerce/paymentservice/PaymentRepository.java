package com.ecommerce.paymentservice;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    /** The payment for an order - used by the saga to charge once and to refund on cancellation. */
    Optional<Payment> findByOrderId(Long orderId);
}
