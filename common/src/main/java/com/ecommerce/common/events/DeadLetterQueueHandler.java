package com.ecommerce.common.events;

import com.ecommerce.common.dlq.DeadLetter;
import com.ecommerce.common.dlq.DeadLetterRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Consumes the {@code *-dlq} topics and parks the messages that failed in <em>this</em> service's own consumer
 * group in the {@code dead_letters} table, where they can be listed and replayed through
 * {@code /api/v1/dead-letters}. Messages from other services' groups are skipped - their own service records
 * them in its own database.
 *
 * <p>This is deliberately not a silent log line: a parked message raises the {@code ecommerce.dlq.parked}
 * gauge, which the {@code DeadLettersParked} alert watches.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DeadLetterQueueHandler {

    private final DeadLetterRepository repository;
    private final ObjectMapper objectMapper;
    private final ObjectProvider<MeterRegistry> meters;

    /** Consumer groups of this service whose failures belong in its dead-letter table. */
    @Value("${ecommerce.dlq.consumer-groups:${spring.kafka.consumer.group-id:}}")
    private List<String> ownGroups;

    private Counter received;

    @PostConstruct
    void registerMetrics() {
        MeterRegistry registry = meters.getIfAvailable();
        if (registry == null) {
            return;
        }
        received = Counter.builder("ecommerce.dlq.received").description("Messages parked in the dead-letter table").register(registry);
        Gauge.builder("ecommerce.dlq.parked", repository, r -> {
                    try {
                        return (double) r.countByStatus(DeadLetter.PARKED);
                    } catch (RuntimeException e) {
                        return Double.NaN;
                    }
                })
                .description("Dead letters waiting for a human to inspect or replay them")
                .register(registry);
    }

    @KafkaListener(id = "${spring.application.name:app}-dlq-listener",
            topicPattern = "${kafka.dlq.pattern:.*-dlq$}",
            groupId = "${spring.application.name:app}-dlq",
            containerFactory = "dlqKafkaListenerContainerFactory")
    @Transactional
    public void handleFailedEvent(ConsumerRecord<String, String> record) {
        String group = headerText(record, KafkaHeaders.DLT_ORIGINAL_CONSUMER_GROUP);
        if (group == null || !ownGroups.contains(group)) {
            return; // another service's failure (or an unattributed record): its owner records it
        }

        String originalTopic = headerText(record, KafkaHeaders.DLT_ORIGINAL_TOPIC);
        Integer partition = headerInt(record, KafkaHeaders.DLT_ORIGINAL_PARTITION);
        Long offset = headerLong(record, KafkaHeaders.DLT_ORIGINAL_OFFSET);
        if (originalTopic == null) {
            originalTopic = record.topic().replaceFirst(Topics.DLQ_SUFFIX + "$", "");
        }
        if (repository.existsByOriginalTopicAndOriginalPartitionAndOriginalOffsetAndConsumerGroup(
                originalTopic, partition, offset, group)) {
            return; // the DLQ consumer itself was redelivered this record
        }

        DeadLetter deadLetter = DeadLetter.builder()
                .originalTopic(originalTopic)
                .originalPartition(partition)
                .originalOffset(offset)
                .messageKey(record.key())
                .payload(record.value())
                .headers(replayableHeaders(record))
                .consumerGroup(group)
                .exceptionClass(headerText(record, KafkaHeaders.DLT_EXCEPTION_FQCN))
                .exceptionMessage(truncate(headerText(record, KafkaHeaders.DLT_EXCEPTION_MESSAGE)))
                .status(DeadLetter.PARKED)
                .receivedAt(Instant.now())
                .build();
        repository.save(deadLetter);
        if (received != null) {
            received.increment();
        }
        log.error("Message from {} (partition {}, offset {}) parked as dead letter {} after retries were exhausted: {}",
                originalTopic, partition, offset, deadLetter.getId(), deadLetter.getExceptionMessage());
    }

    /** The headers a replay must restore (type, event id, correlation...), minus the dead-letter bookkeeping. */
    private String replayableHeaders(ConsumerRecord<String, String> record) {
        Map<String, String> kept = new LinkedHashMap<>();
        for (Header header : record.headers()) {
            String name = header.key();
            if (name.startsWith("kafka_dlt-") || name.startsWith("kafka_exception")
                    || name.startsWith("springDeserializerException") || header.value() == null) {
                continue;
            }
            kept.put(name, new String(header.value(), StandardCharsets.UTF_8));
        }
        try {
            return objectMapper.writeValueAsString(kept);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot serialize dead-letter headers", e);
        }
    }

    private static String headerText(ConsumerRecord<?, ?> record, String name) {
        Header header = record.headers().lastHeader(name);
        return header == null || header.value() == null ? null : new String(header.value(), StandardCharsets.UTF_8);
    }

    private static Integer headerInt(ConsumerRecord<?, ?> record, String name) {
        Header header = record.headers().lastHeader(name);
        return header == null || header.value() == null || header.value().length != Integer.BYTES
                ? null : ByteBuffer.wrap(header.value()).getInt();
    }

    private static Long headerLong(ConsumerRecord<?, ?> record, String name) {
        Header header = record.headers().lastHeader(name);
        return header == null || header.value() == null || header.value().length != Long.BYTES
                ? null : ByteBuffer.wrap(header.value()).getLong();
    }

    private static String truncate(String text) {
        return text == null || text.length() <= 1000 ? text : text.substring(0, 1000);
    }
}
