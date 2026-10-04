package com.ecommerce.common.events;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * Last-resort safety net for the saga's Kafka listeners (OrderEventListener,
 * InventoryEventListener, PaymentEventListener): when a listener's business
 * logic throws, routing the failed event here - instead of just logging and
 * swallowing the exception - lets the listener still acknowledge the
 * original message afterwards. With {@code AckMode.MANUAL}, acknowledging a
 * later message commits past an unacknowledged earlier one, so a swallowed
 * exception with no ack doesn't "retry later" - it silently and permanently
 * drops that event. Parking it in {@code <topic>-dlq} first means the
 * message is still safe for inspection/replay instead of lost. See
 * DeadLetterQueueHandler for the consumer side.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DlqPublisher {

    private final KafkaTemplate<String, String> dlqKafkaTemplate;
    private final ObjectMapper objectMapper;

    /**
     * @param event the event that failed to process
     * @param originalTopic the topic it was consumed from (the DLQ topic is
     *                       {@code originalTopic + "-dlq"} - see KafkaEventConfig's NewTopic beans)
     * @param cause why processing failed
     */
    public void publish(DomainEvent event, String originalTopic, Throwable cause) {
        String dlqTopic = originalTopic + "-dlq";
        try {
            String payload = objectMapper.writeValueAsString(event);
            dlqKafkaTemplate.send(dlqTopic, event.getEventId(), payload)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish event {} (type={}) to DLQ topic {} - it will be lost",
                            event.getEventId(), event.getEventType(), dlqTopic, ex);
                    } else {
                        log.warn("Routed failed event {} (type={}) from {} to {} - reason: {}",
                            event.getEventId(), event.getEventType(), originalTopic, dlqTopic, cause.getMessage());
                    }
                });
        } catch (Exception e) {
            log.error("Could not serialize event {} (type={}) for DLQ topic {} - it will be lost",
                event.getEventId(), event.getEventType(), dlqTopic, e);
        }
    }
}
