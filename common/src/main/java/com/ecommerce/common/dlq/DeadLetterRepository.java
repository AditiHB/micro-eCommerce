package com.ecommerce.common.dlq;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DeadLetterRepository extends JpaRepository<DeadLetter, Long> {

    Page<DeadLetter> findByStatus(String status, Pageable pageable);

    long countByStatus(String status);

    boolean existsByOriginalTopicAndOriginalPartitionAndOriginalOffsetAndConsumerGroup(
            String originalTopic, Integer originalPartition, Long originalOffset, String consumerGroup);
}
