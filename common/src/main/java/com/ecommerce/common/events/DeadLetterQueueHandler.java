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

    @KafkaListener(topics = "${kafka.dlq.pattern:.*-dlq$}",
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

    @KafkaListener(topics = "order-created-dlq",
                   groupId = "order-dlq-handler",
                   containerFactory = "dlqKafkaListenerContainerFactory")
    public void handleOrderCreatedDlq(@Payload String message,
                                     @Header(value = KafkaHeaders.RECEIVED_TOPIC) String topic,
                                     Acknowledgment ack) {
        try {
            log.error("Order created DLQ event: {}", message);
            ack.acknowledge();
        } catch (Exception e) {
            log.error("Error handling order created DLQ", e);
        }
    }

    @KafkaListener(topics = "payment-processed-dlq",
                   groupId = "payment-dlq-handler",
                   containerFactory = "dlqKafkaListenerContainerFactory")
    public void handlePaymentProcessedDlq(@Payload String message,
                                         @Header(value = KafkaHeaders.RECEIVED_TOPIC) String topic,
                                         Acknowledgment ack) {
        try {
            log.error("Payment processed DLQ event: {}", message);
            ack.acknowledge();
        } catch (Exception e) {
            log.error("Error handling payment processed DLQ", e);
        }
    }

    @KafkaListener(topics = "inventory-reserved-dlq",
                   groupId = "inventory-dlq-handler",
                   containerFactory = "dlqKafkaListenerContainerFactory")
    public void handleInventoryReservedDlq(@Payload String message,
                                          @Header(value = KafkaHeaders.RECEIVED_TOPIC) String topic,
                                          Acknowledgment ack) {
        try {
            log.error("Inventory reserved DLQ event: {}", message);
            ack.acknowledge();
        } catch (Exception e) {
            log.error("Error handling inventory reserved DLQ", e);
        }
    }
}
