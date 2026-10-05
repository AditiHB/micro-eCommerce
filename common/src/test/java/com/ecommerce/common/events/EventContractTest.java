package com.ecommerce.common.events;

import com.ecommerce.common.config.JacksonConfig;
import com.ecommerce.common.testsupport.EventSamples;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The event contract, pinned. Each published event type has a golden JSON file
 * ({@code src/test/resources/contracts/<type>.v<version>.json}) that is its wire format. Producers must still
 * emit exactly it and consumers must still read it, so a change that would break a consumer that has not been
 * rebuilt yet fails here, in the pull request, instead of in production.
 *
 * <p>To change a contract on purpose, follow {@code docs/EVENT_CONTRACTS.md}: additive changes only (new optional
 * fields); anything else is a new type name. Regenerate the files with {@code -Dcontracts.update=true}.
 */
@DisplayName("Event contracts")
class EventContractTest {

    private static final ObjectMapper MAPPER = JacksonConfig.newObjectMapper();
    private static final Pattern LOGICAL_NAME = Pattern.compile("^[a-z]+(\\.[a-z]+)+$");
    private static final boolean UPDATE = Boolean.getBoolean("contracts.update");

    private static String resource(DomainEvent event) {
        return "contracts/" + event.getEventType() + ".v" + event.getVersion() + ".json";
    }

    @TestFactory
    @DisplayName("every event is written exactly as its golden file")
    Stream<DynamicTest> producerWritesTheGoldenFile() {
        return EventSamples.all().values().stream().map(event -> DynamicTest.dynamicTest(event.getEventType(), () -> {
            JsonNode actual = MAPPER.valueToTree(event);
            if (UPDATE) {
                Path file = Path.of("src/test/resources", resource(event));
                Files.createDirectories(file.getParent());
                Files.writeString(file, MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(actual) + "\n");
            }
            assertThat((Object) normalize(actual)).as("wire format of %s", event.getEventType()).isEqualTo(golden(event));
        }));
    }

    @TestFactory
    @DisplayName("every golden file can still be read by a consumer")
    Stream<DynamicTest> consumerReadsTheGoldenFile() {
        return EventSamples.all().values().stream().map(event -> DynamicTest.dynamicTest(event.getEventType(), () -> {
            DomainEvent read = MAPPER.treeToValue(golden(event), event.getClass());
            assertThat((Object) normalize(MAPPER.valueToTree(read))).isEqualTo(golden(event));
            assertThat(read.getEventId()).isNotBlank();
            assertThat(read.getOccurredAt()).isEqualTo(EventSamples.OCCURRED_AT);
        }));
    }

    @TestFactory
    @DisplayName("a consumer ignores fields it does not know (a producer may add optional fields)")
    Stream<DynamicTest> tolerantReader() {
        return EventSamples.all().values().stream().map(event -> DynamicTest.dynamicTest(event.getEventType(), () -> {
            ObjectNode future = golden(event).deepCopy();
            future.put("fieldAddedInALaterVersion", "x");
            future.putObject("anotherNewObject").put("nested", 1);

            DomainEvent read = MAPPER.treeToValue(future, event.getClass());

            assertThat((Object) normalize(MAPPER.valueToTree(read))).isEqualTo(golden(event));
        }));
    }

    @Test
    @DisplayName("every registered event type has a sample and a golden file, and nothing else does")
    void catalogAndContractsAgree() throws IOException {
        Set<String> registered = EventCatalog.all().stream()
                .filter(c -> c.getPackageName().startsWith("com.ecommerce.common.events"))
                .map(EventCatalog::typeOf)
                .collect(Collectors.toCollection(TreeSet::new));

        assertThat(EventSamples.all().keySet()).containsExactlyInAnyOrderElementsOf(registered);
        for (DomainEvent event : EventSamples.all().values()) {
            assertThat(getClass().getClassLoader().getResource(resource(event)))
                    .as("golden file for %s", event.getEventType()).isNotNull();
        }
    }

    @Test
    @DisplayName("type names are stable logical names, never Java class names")
    void typeNamesAreLogical() {
        for (Class<? extends DomainEvent> eventClass : EventCatalog.all()) {
            assertThat(EventCatalog.typeOf(eventClass)).matches(LOGICAL_NAME);
            assertThat(EventCatalog.typeOf(eventClass)).doesNotContain(eventClass.getSimpleName());
        }
    }

    @Test
    @DisplayName("nothing on the wire names a Java class, and timestamps are ISO-8601 UTC")
    void noJavaDetailsOnTheWire() throws IOException {
        for (DomainEvent event : EventSamples.all().values()) {
            String json = MAPPER.writeValueAsString(event);
            assertThat(json).doesNotContain("com.ecommerce").doesNotContain("java.");
            assertThat(MAPPER.readTree(json).get("occurredAt").asText()).isEqualTo("2026-01-01T10:15:30Z");
        }
    }

    @Test
    @DisplayName("every event type is routed to a topic the catalog knows")
    void everyEventHasATopic() {
        Map<String, String> topics = EventCatalog.all().stream()
                .collect(Collectors.toMap(EventCatalog::typeOf, EventCatalog::topicOf));
        assertThat(topics).containsEntry("order.created", Topics.ORDER_CREATED)
                .containsEntry("inventory.reserved", Topics.INVENTORY_RESERVED)
                .containsEntry("payment.processed", Topics.PAYMENT_PROCESSED);
        assertThat(EventCatalog.topics()).contains(Topics.ORDER_CANCELLED, Topics.REFUND_COMPLETED, Topics.INVENTORY_RELEASED);
    }

    /** Round-trips through text so a number built from a BigDecimal compares equal to one parsed from the file. */
    private static JsonNode normalize(JsonNode node) throws IOException {
        return MAPPER.readTree(MAPPER.writeValueAsString(node));
    }

    private JsonNode golden(DomainEvent event) throws IOException {
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(resource(event))) {
            assertThat(in).as("golden file %s", resource(event)).isNotNull();
            return MAPPER.readTree(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        }
    }
}
