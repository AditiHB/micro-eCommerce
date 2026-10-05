package com.ecommerce.common.outbox;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface OutboxRepository extends JpaRepository<OutboxEvent, Long> {

    /**
     * Locks and returns the next events that are due, oldest first.
     *
     * <ul>
     *   <li>{@code FOR UPDATE SKIP LOCKED} lets several replicas relay at the same time without ever sending
     *       the same row twice: a row another replica holds is skipped, not waited for.</li>
     *   <li>The {@code NOT EXISTS} makes an event wait for any earlier pending event of the same aggregate -
     *       even one another replica has locked or is backing off - so one order's events leave in the order
     *       they were written. A batch therefore holds at most one event per aggregate.</li>
     * </ul>
     */
    @Query(value = """
            SELECT o.* FROM outbox_event o
            WHERE o.status = 'PENDING'
              AND o.next_attempt_at <= :now
              AND NOT EXISTS (SELECT 1 FROM outbox_event e
                              WHERE e.aggregate_type = o.aggregate_type
                                AND e.aggregate_id = o.aggregate_id
                                AND e.status = 'PENDING'
                                AND e.id < o.id)
            ORDER BY o.id
            LIMIT :limit
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<OutboxEvent> lockDueBatch(@Param("now") Instant now, @Param("limit") int limit);

    Optional<OutboxEvent> findByEventId(String eventId);

    long countByStatus(String status);

    @Query("SELECT MIN(o.createdAt) FROM OutboxEvent o WHERE o.status = 'PENDING'")
    Optional<Instant> oldestPendingCreatedAt();

    @Modifying
    @Query("DELETE FROM OutboxEvent o WHERE o.status = 'PUBLISHED' AND o.publishedAt < :cutoff")
    int deletePublishedBefore(@Param("cutoff") Instant cutoff);
}
