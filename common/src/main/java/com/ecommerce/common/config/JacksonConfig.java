package com.ecommerce.common.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.json.ProblemDetailJacksonMixin;
import org.springframework.context.annotation.Configuration;

@Configuration
public class JacksonConfig {

    @Bean
    public ObjectMapper objectMapper() {
        return newObjectMapper();
    }

    /** The mapper every event is written with: ISO-8601 dates, never numeric timestamps. */
    public static ObjectMapper newObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        // Without this mixin an RFC 9457 problem's extension members (errorCode, errors, ...) are written
        // nested under a "properties" object instead of at the top level where the RFC puts them.
        mapper.addMixIn(ProblemDetail.class, ProblemDetailJacksonMixin.class);
        return mapper;
    }
}
