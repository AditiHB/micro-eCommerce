package com.ecommerce.inventoryservice;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface InventoryRepository extends JpaRepository<Inventory, Long> {

    Optional<Inventory> findByProductId(String productId);

    /**
     * Locks the stock rows of one order's products, always in product order so two orders that share products
     * can never lock them in opposite orders and deadlock.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT i FROM Inventory i WHERE i.productId IN :productIds ORDER BY i.productId")
    List<Inventory> lockByProductIds(@Param("productIds") Collection<String> productIds);

    /**
     * Takes stock in ONE statement: the check ("is there enough?") and the decrement cannot be separated by
     * another writer, so stock can never be oversold. The {@code WHERE quantity >= :n} makes taking exactly what
     * is left (down to zero) succeed and taking more fail, with no read-modify-write cycle to lose an update.
     *
     * @return 1 if the stock was taken, 0 if the product does not exist or has too little
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE Inventory i SET i.quantity = i.quantity - :n, i.version = i.version + 1 "
            + "WHERE i.productId = :productId AND i.quantity >= :n")
    int decrementIfAvailable(@Param("productId") String productId, @Param("n") int n);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE Inventory i SET i.quantity = i.quantity + :n, i.version = i.version + 1 WHERE i.productId = :productId")
    int increment(@Param("productId") String productId, @Param("n") int n);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE Inventory i SET i.quantity = i.quantity - :n, i.version = i.version + 1 "
            + "WHERE i.id = :id AND i.quantity >= :n")
    int decrementByIdIfAvailable(@Param("id") Long id, @Param("n") int n);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE Inventory i SET i.quantity = i.quantity + :n, i.version = i.version + 1 WHERE i.id = :id")
    int incrementById(@Param("id") Long id, @Param("n") int n);
}
