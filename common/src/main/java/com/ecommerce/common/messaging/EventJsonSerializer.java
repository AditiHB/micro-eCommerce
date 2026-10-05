package com.ecommerce.common.messaging;

import com.ecommerce.common.config.JacksonConfig;
import com.ecommerce.common.events.DomainEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.common.errors.SerializationException;
import org.apache.kafka.common.header.Headers;
import org.apache.kafka.common.serialization.Serializer;

import java.nio.charset.StandardCharsets;

/**
 * Writes an event exactly as the outbox relay does - ISO-8601 JSON with the logical type name in the type
 * header - for the one place that re-serializes an already-deserialized event: the dead-letter publisher.
 */
public class EventJsonSerializer implements Serializer<Object> {

    public static final String TYPE_HEADER = "__TypeId__";

    private final ObjectMapper mapper = JacksonConfig.newObjectMapper();

    @Override
    public byte[] serialize(String topic, Object data) {
        return serialize(topic, null, data);
    }

    @Override
    public byte[] serialize(String topic, Headers headers, Object data) {
        if (data == null) {
            return null;
        }
        if (headers != null && data instanceof DomainEvent event) {
            headers.remove(TYPE_HEADER);
            headers.add(TYPE_HEADER, event.getEventType().getBytes(StandardCharsets.UTF_8));
        }
        try {
            return mapper.writeValueAsBytes(data);
        } catch (JsonProcessingException e) {
            throw new SerializationException("Cannot serialize " + data.getClass().getName(), e);
        }
    }
}
