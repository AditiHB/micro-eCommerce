package com.ecommerce.common.web;

import com.ecommerce.common.exception.PreconditionFailedException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("ETag / If-Match")
class EntityTagsTest {

    @Test
    @DisplayName("the ETag is the resource version as a strong validator")
    void etagFormat() {
        assertThat(EntityTags.of(3L)).isEqualTo("\"3\"");
        assertThat(EntityTags.of(null)).isEqualTo("\"0\"");
    }

    @Test
    @DisplayName("no If-Match means no check was requested")
    void absentHeaderIsAccepted() {
        assertThatCode(() -> EntityTags.verifyIfMatch(null, 3L)).doesNotThrowAnyException();
        assertThatCode(() -> EntityTags.verifyIfMatch("  ", 3L)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("a matching If-Match is accepted, in any of the usual spellings")
    void matchingHeaderIsAccepted() {
        assertThatCode(() -> EntityTags.verifyIfMatch("\"3\"", 3L)).doesNotThrowAnyException();
        assertThatCode(() -> EntityTags.verifyIfMatch("W/\"3\"", 3L)).doesNotThrowAnyException();
        assertThatCode(() -> EntityTags.verifyIfMatch("\"1\", \"3\"", 3L)).doesNotThrowAnyException();
        assertThatCode(() -> EntityTags.verifyIfMatch("*", 3L)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("a stale If-Match is rejected with 412 - the lost update is prevented")
    void staleHeaderIsRejected() {
        assertThatThrownBy(() -> EntityTags.verifyIfMatch("\"2\"", 3L))
                .isInstanceOf(PreconditionFailedException.class)
                .hasMessageContaining("current version 3");
    }
}
