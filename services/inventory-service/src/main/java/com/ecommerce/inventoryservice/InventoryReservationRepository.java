package com.ecommerce.inventoryservice;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface InventoryReservationRepository extends JpaRepository<InventoryReservation, Long> {

    List<InventoryReservation> findByOrderId(Long orderId);

    boolean existsByOrderId(Long orderId);

    /**
     * The order's reservations that still hold stock, locked so that two replicas handling the same
     * cancellation cannot both give the stock back.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM InventoryReservation r WHERE r.orderId = :orderId AND r.releasedAt IS NULL ORDER BY r.productId")
    List<InventoryReservation> lockActiveByOrderId(@Param("orderId") Long orderId);
}
