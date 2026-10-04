package com.ecommerce.inventoryservice;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface InventoryReservationRepository extends JpaRepository<InventoryReservation, Long> {
    Optional<InventoryReservation> findByOrderId(Long orderId);

    /**
     * Atomically transitions one order's reservation from active to
     * released - the conditional WHERE is what makes this safe against two
     * concurrent redeliveries of the same payment-failed event: only one
     * can ever affect a row, so only one will see {@code updated == 1} and
     * actually increment the stock back.
     *
     * @return 1 if this call released it, 0 if it was already released (or didn't exist)
     */
    @Modifying
    @Query("UPDATE InventoryReservation r SET r.releasedAt = CURRENT_TIMESTAMP WHERE r.orderId = :orderId AND r.releasedAt IS NULL")
    int markReleased(@Param("orderId") Long orderId);
}
