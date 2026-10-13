package com.ecommerce.orderservice;

import com.ecommerce.common.enums.OrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

public interface OrderRepository extends JpaRepository<Order, Long> {

    /** One customer's orders - what a plain USER is allowed to list. */
    Page<Order> findByCustomerId(Long customerId, Pageable pageable);

    /** Orders still moving through the saga that have not changed for a while: the saga deadline's work list. */
    @Query("SELECT o.id FROM Order o WHERE o.status IN :open AND o.updatedAt < :cutoff ORDER BY o.updatedAt")
    List<Long> findStaleOpenOrderIds(@Param("open") Collection<OrderStatus> open,
                                     @Param("cutoff") LocalDateTime cutoff, Pageable limit);

    /** Completed orders with no invoice row yet: the invoice batch job's work list (sort order is the
     *  reader's job, via the {@link Pageable} it is given, not this query). */
    @Query("SELECT o FROM Order o WHERE o.status = com.ecommerce.common.enums.OrderStatus.COMPLETED "
            + "AND NOT EXISTS (SELECT 1 FROM Invoice i WHERE i.orderId = o.id)")
    Page<Order> findCompletedWithoutInvoice(Pageable pageable);
}
