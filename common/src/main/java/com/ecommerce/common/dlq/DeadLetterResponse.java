package com.ecommerce.common.dlq;

import java.time.Instant;

/** What the dead-letter API shows about a parked message. */
public record DeadLetterResponse(
        Long id,
        String originalTopic,
        Integer originalPartition,
        Long originalOffset,
        String messageKey,
        String consumerGroup,
        String exceptionClass,
        String exceptionMessage,
        String payload,
        String status,
        Instant receivedAt,
        Instant replayedAt) {

    static DeadLetterResponse of(DeadLetter d) {
        return new DeadLetterResponse(d.getId(), d.getOriginalTopic(), d.getOriginalPartition(), d.getOriginalOffset(),
                d.getMessageKey(), d.getConsumerGroup(), d.getExceptionClass(), d.getExceptionMessage(), d.getPayload(),
                d.getStatus(), d.getReceivedAt(), d.getReplayedAt());
    }
}
