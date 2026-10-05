package com.ecommerce.productservice;

import com.ecommerce.common.config.JacksonConfig;
import com.ecommerce.common.events.DomainEvent;
import com.ecommerce.productservice.event.ProductCreatedEvent;
import com.ecommerce.productservice.event.ProductDeletedEvent;
import com.ecommerce.productservice.event.ProductUpdatedEvent;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The product events' wire format, pinned by golden files exactly like the saga events (see
 * {@code docs/EVENT_CONTRACTS.md}): the producer must keep writing them, and a consumer must keep reading them,
 * including with fields it does not know. Regenerate with {@code -Dcontracts.update=true}.
 */
@DisplayName("Product event contracts")
class ProductEventContractTest {

    private static final ObjectMapper MAPPER = JacksonConfig.newObjectMapper();
    private static final boolean UPDATE = Boolean.getBoolean("contracts.update");

    private static List<DomainEvent> samples() {
        List<DomainEvent> events = List.of(
                new ProductCreatedEvent(7L, "Headphones", "SKU-001", new BigDecimal("79.99"), "USD", "Electronics"),
                new ProductUpdatedEvent(7L, "Headphones", "SKU-001", new BigDecimal("89.99"), "USD", "Electronics"),
                new ProductDeletedEvent(7L, "SKU-001"));
        events.forEach(e -> {
            e.setEventId("evt-" + e.getEventType());
            e.setOccurredAt(Instant.parse("2026-01-01T10:15:30Z"));
        });
        return events;
    }

    private static String resource(DomainEvent event) {
        return "contracts/" + event.getEventType() + ".v" + event.getVersion() + ".json";
    }

    private static JsonNode golden(DomainEvent event) throws IOException {
        try (InputStream in = ProductEventContractTest.class.getClassLoader().getResourceAsStream(resource(event))) {
            assertThat(in).as("golden file %s", resource(event)).isNotNull();
            return MAPPER.readTree(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        }
    }

    private static JsonNode normalize(JsonNode node) throws IOException {
        return MAPPER.readTree(MAPPER.writeValueAsString(node));
    }

    @TestFactory
    @DisplayName("each product event is written exactly as its golden file")
    Stream<DynamicTest> producerWritesTheGoldenFile() {
        return samples().stream().map(event -> DynamicTest.dynamicTest(event.getEventType(), () -> {
            JsonNode actual = MAPPER.valueToTree(event);
            if (UPDATE) {
                Path file = Path.of("src/test/resources", resource(event));
                Files.createDirectories(file.getParent());
                Files.writeString(file, MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(actual) + "\n");
            }
            assertThat((Object) normalize(actual)).isEqualTo(golden(event));
        }));
    }

    @TestFactory
    @DisplayName("each golden file is readable by a consumer, also when the producer has added fields")
    Stream<DynamicTest> consumerReadsTheGoldenFile() {
        return samples().stream().map(event -> DynamicTest.dynamicTest(event.getEventType(), () -> {
            var withFutureField = ((com.fasterxml.jackson.databind.node.ObjectNode) golden(event).deepCopy()).put("addedLater", true);

            DomainEvent read = MAPPER.treeToValue(withFutureField, event.getClass());

            assertThat((Object) normalize(MAPPER.valueToTree(read))).isEqualTo(golden(event));
        }));
    }
}
