package com.ecommerce.common.dlq;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * A message this service could not process even after its retries, parked here so it can be inspected and, once
 * the cause is fixed, replayed. It is only ever recorded for messages that failed in <em>this</em> service's own
 * consumer group.
 */
@Entity
@Table(name = "dead_letters")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DeadLetter {

    public static final String PARKED = "PARKED";
    public static final String REPLAYED = "REPLAYED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "original_topic", nullable = false, length = 200)
    private String originalTopic;

    @Column(name = "original_partition")
    private Integer originalPartition;

    @Column(name = "original_offset")
    private Long originalOffset;

    @Column(name = "message_key", length = 200)
    private String messageKey;

    @Column(columnDefinition = "TEXT")
    private String payload;

    /** JSON object of the message headers worth keeping (type, event id, correlation, ...). */
    @Column(columnDefinition = "TEXT")
    private String headers;

    @Column(name = "consumer_group", nullable = false, length = 100)
    private String consumerGroup;

    @Column(name = "exception_class", length = 300)
    private String exceptionClass;

    @Column(name = "exception_message", length = 1000)
    private String exceptionMessage;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(name = "received_at", nullable = false)
    private Instant receivedAt;

    @Column(name = "replayed_at")
    private Instant replayedAt;
}
