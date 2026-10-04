package com.ecommerce.common.events;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class DeadLetterQueueHandler {

    /**
     * Consumes every {@code *-dlq} topic (order-created-dlq,
     * payment-processed-dlq, payment-failed-dlq, inventory-reserved-dlq,
     * inventory-failed-dlq - see KafkaEventConfig's NewTopic beans) that
     * DlqPublisher routes a saga listener's failed event to.
     *
     * TODO (known gap, fixed from a worse one): this was previously
     * {@code @KafkaListener(topics = "${kafka.dlq.pattern:.*-dlq$}", ...)} -
     * `topics` takes literal topic names, not a regex, so Kafka rejected
     * ".*-dlq$" as an invalid topic on every poll (visible as a continuous
     * InvalidTopicException/"Consumer exception" loop in any service
     * scanning this class). `topicPattern` is the attribute that actually
     * matches topics by regex. This single listener replaces three
     * near-identical ones that each subscribed to one literal DLQ topic
     * name (now redundant since this pattern covers all of them, including
     * payment-failed-dlq and inventory-failed-dlq, which never had a
     * listener here at all).
     *
     * Currently logs only - there's no reprocessing/alerting here yet, so
     * these messages still need manual inspection/replay.
     */
    @KafkaListener(topicPattern = "${kafka.dlq.pattern:.*-dlq$}",
                   groupId = "dlq-handler-group",
                   containerFactory = "dlqKafkaListenerContainerFactory")
    public void handleFailedEvent(@Payload String message,
                                 @Header(value = KafkaHeaders.RECEIVED_TOPIC) String topic,
                                 @Header(value = "kafka_receivedPartitionId") int partition,
                                 @Header(value = KafkaHeaders.OFFSET) long offset,
                                 Acknowledgment ack) {
        try {
            log.error("Processing DLQ message from topic: {}, partition: {}, offset: {}. Message: {}",
                topic, partition, offset, message);

            log.warn("Failed event has been moved to DLQ: topic={}, partition={}, offset={}",
                topic, partition, offset);

            ack.acknowledge();
        } catch (Exception e) {
            log.error("Error handling DLQ message from topic: {}", topic, e);
        }
    }
}
