package e2e;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;

import java.time.Duration;
import java.util.Collections;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.ExecutionException;

/**
 * Publishes a raw, hand-crafted event straight onto a main Kafka topic,
 * bypassing every service's own producer entirely - lets dlq-routing.feature
 * choose field values (e.g. a null orderId) that the normal REST-validated
 * flow could never produce, so a saga listener's actual exception-handling
 * path, and its publish to {@code <topic>-dlq}, gets exercised
 * deterministically without stopping any shared infrastructure.
 *
 * Talks to the broker on the host-exposed PLAINTEXT_HOST listener
 * (localhost:9092 - see docker-compose.yml's kafka service), the same one
 * every other host-side tool (kafka-console-producer, etc.) uses.
 */
public class KafkaFaultInjector {

    private static final String BOOTSTRAP_SERVERS = "localhost:9092";

    /**
     * @param topic the main topic to publish onto (e.g. "order-created")
     * @param key the record key (any non-null string is fine - these topics aren't compacted)
     * @param eventType the event's LOGICAL type name (e.g. "order.created") - written to the __TypeId__
     *                  header, which is the only type information on the wire. The consumers map it to a
     *                  class through an allow-list, so a name that is not in the event catalog (or a Java
     *                  class name) is refused rather than instantiated.
     * @param json the event body, hand-crafted JSON (or deliberately not JSON, to test poison messages)
     */
    public static void publishRaw(String topic, String key, String eventType, String json) {
        Properties props = new Properties();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, BOOTSTRAP_SERVERS);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());

        try (KafkaProducer<String, String> producer = new KafkaProducer<>(props)) {
            ProducerRecord<String, String> record = new ProducerRecord<>(topic, key, json);
            record.headers().add(new RecordHeader("__TypeId__", eventType.getBytes()));
            producer.send(record).get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted publishing raw event to " + topic, e);
        } catch (ExecutionException e) {
            throw new RuntimeException("Failed to publish raw event to " + topic, e.getCause());
        }
    }

    /**
     * Polls {@code dlqTopic} from the beginning (a fresh, unique consumer
     * group every call) looking for a message containing
     * {@code expectedEventId} - i.e. confirms DlqPublisher actually routed
     * that specific failed event there. Does not use the application's own
     * "dlq-handler-group" - a separate group reads the topic independently
     * without disturbing DeadLetterQueueHandler's own consumption of it.
     *
     * @return true if found within timeoutSeconds, false otherwise
     */
    public static boolean waitForDlqMessage(String dlqTopic, String expectedEventId, int timeoutSeconds) {
        return findDlqMessage(dlqTopic, expectedEventId, timeoutSeconds) != null;
    }

    /**
     * Same lookup as {@link #waitForDlqMessage}, but returns the matched
     * message body (or null if not found within the timeout) so the caller
     * can print the actual DLQ payload - see DataShowcase.showRaw and
     * dlq-routing.feature.
     */
    public static String findDlqMessage(String dlqTopic, String expectedEventId, int timeoutSeconds) {
        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, BOOTSTRAP_SERVERS);
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "e2e-dlq-verification-" + UUID.randomUUID());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(props)) {
            consumer.subscribe(Collections.singletonList(dlqTopic));
            long deadline = System.currentTimeMillis() + timeoutSeconds * 1000L;

            while (System.currentTimeMillis() < deadline) {
                ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(500));
                for (ConsumerRecord<String, String> record : records) {
                    if (record.value() != null && record.value().contains(expectedEventId)) {
                        return record.value();
                    }
                }
            }
            return null;
        }
    }
}
