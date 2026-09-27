package com.ecommerce.common.eventsourcing;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface EventStoreRepository extends JpaRepository<EventStore, Long> {

    @Query("SELECT e FROM EventStore e WHERE e.eventId = :eventId")
    Optional<EventStore> findByEventId(@Param("eventId") String eventId);

    @Query("SELECT e FROM EventStore e WHERE e.aggregateId = :aggregateId ORDER BY e.version ASC")
    List<EventStore> findByAggregateId(@Param("aggregateId") String aggregateId);

    @Query("SELECT e FROM EventStore e WHERE e.aggregateId = :aggregateId AND e.aggregateType = :aggregateType ORDER BY e.version ASC")
    List<EventStore> findByAggregateIdAndType(@Param("aggregateId") String aggregateId, @Param("aggregateType") String aggregateType);

    @Query("SELECT e FROM EventStore e WHERE e.aggregateType = :aggregateType ORDER BY e.version ASC")
    List<EventStore> findByAggregateType(@Param("aggregateType") String aggregateType);

    @Query("SELECT e FROM EventStore e WHERE e.eventType = :eventType ORDER BY e.occurredAt DESC")
    List<EventStore> findByEventType(@Param("eventType") String eventType);

    @Query("SELECT e FROM EventStore e WHERE e.occurredAt >= :from AND e.occurredAt <= :to ORDER BY e.occurredAt ASC")
    List<EventStore> findEventsBetween(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    @Query("SELECT e FROM EventStore e WHERE e.aggregateId = :aggregateId AND e.version > :version ORDER BY e.version ASC")
    List<EventStore> findEventsSince(@Param("aggregateId") String aggregateId, @Param("version") int version);

    @Query("SELECT e FROM EventStore e WHERE e.aggregateId = :aggregateId AND e.aggregateType = :aggregateType ORDER BY e.version DESC LIMIT 1")
    Optional<EventStore> findLatestEventForAggregate(@Param("aggregateId") String aggregateId, @Param("aggregateType") String aggregateType);

    @Query("SELECT e FROM EventStore e WHERE e.correlationId = :correlationId ORDER BY e.occurredAt ASC")
    List<EventStore> findByCorrelationId(@Param("correlationId") String correlationId);

    Page<EventStore> findAll(Pageable pageable);

    long countByAggregateId(String aggregateId);

    long countByAggregateType(String aggregateType);
}
