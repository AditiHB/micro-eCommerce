package com.ecommerce.common.events;

import com.ecommerce.common.dlq.DeadLetter;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * The way back out of the dead-letter queue: puts a parked message onto its original topic again, with the
 * key and headers it had, so it is processed like a fresh delivery.
 *
 * <p>(Dead-lettering itself is done by the container's error handler after the retries are exhausted - see
 * {@code KafkaEventConfig} - never by listener code.) Replaying is safe even if some consumer group already
 * handled the message: every handler de-duplicates on the event id.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DlqPublisher {

    private static final Duration SEND_TIMEOUT = Duration.ofSeconds(10);

    private final KafkaTemplate<String, String> outboxKafkaTemplate;
    private final ObjectMapper objectMapper;

    /**
     * Sends the parked message back to its original topic and waits for the broker to acknowledge it.
     *
     * @throws IllegalStateException if the broker did not acknowledge it - the dead letter then stays parked
     */
    public void replay(DeadLetter deadLetter) {
        try {
            Map<String, String> headers = deadLetter.getHeaders() == null ? Map.of()
                    : objectMapper.readValue(deadLetter.getHeaders(), new TypeReference<>() { });

            ProducerRecord<String, String> record = new ProducerRecord<>(
                    deadLetter.getOriginalTopic(), null, deadLetter.getMessageKey(), deadLetter.getPayload());
            headers.forEach((name, value) -> record.headers().add(name, value.getBytes(StandardCharsets.UTF_8)));

            outboxKafkaTemplate.send(record).get(SEND_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
            log.warn("Replayed dead letter {} to {}", deadLetter.getId(), deadLetter.getOriginalTopic());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while replaying dead letter " + deadLetter.getId(), e);
        } catch (ExecutionException | TimeoutException | java.io.IOException e) {
            throw new IllegalStateException("Could not replay dead letter " + deadLetter.getId() + ": " + e.getMessage(), e);
        }
    }
}
