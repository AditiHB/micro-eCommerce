package com.ecommerce.notificationservice;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import com.ecommerce.common.enums.NotificationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, Long> {

    Page<Notification> findByCustomerId(Long customerId, Pageable pageable);

    List<Notification> findByOrderId(Long orderId);

    Optional<Notification> findBySourceEventId(String sourceEventId);

    boolean existsBySourceEventId(String sourceEventId);

    /**
     * Locks the next PENDING notifications that are due. {@code SKIP LOCKED} lets several replicas dispatch at
     * once without ever sending the same notification twice.
     */
    @Query(value = """
            SELECT * FROM notifications
            WHERE status = 'PENDING' AND next_attempt_at <= :now
            ORDER BY next_attempt_at
            LIMIT :limit
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<Notification> lockDue(@Param("now") Instant now, @Param("limit") int limit);

    long countByStatus(NotificationStatus status);
}
