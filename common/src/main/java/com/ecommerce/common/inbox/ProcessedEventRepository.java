package com.ecommerce.common.inbox;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;

public interface ProcessedEventRepository extends JpaRepository<ProcessedEvent, ProcessedEvent.Key> {

    /** Atomically claims an event for a consumer: 1 if this call claimed it, 0 if it was already claimed. */
    @Modifying
    @Query(value = """
            INSERT INTO processed_events (consumer, event_id, processed_at)
            VALUES (:consumer, :eventId, :now)
            ON CONFLICT (consumer, event_id) DO NOTHING
            """, nativeQuery = true)
    int claim(@Param("consumer") String consumer, @Param("eventId") String eventId, @Param("now") Instant now);

    @Modifying
    @Query("DELETE FROM ProcessedEvent p WHERE p.processedAt < :cutoff")
    int deleteProcessedBefore(@Param("cutoff") Instant cutoff);
}
