package com.ecommerce.common.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("AudienceValidator")
class AudienceValidatorTest {

    private final AudienceValidator validator = new AudienceValidator("ecommerce-api");

    private Jwt tokenFor(String... audience) {
        Jwt.Builder builder = Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60))
                .subject("someone");
        if (audience.length > 0) {
            builder.audience(java.util.List.of(audience));
        }
        return builder.build();
    }

    @Test
    @DisplayName("accepts a token minted for this API")
    void accepts() {
        assertThat(validator.validate(tokenFor("account", "ecommerce-api")).hasErrors()).isFalse();
    }

    @Test
    @DisplayName("rejects a token minted for another API")
    void rejectsOtherAudience() {
        assertThat(validator.validate(tokenFor("some-other-api")).hasErrors()).isTrue();
    }

    @Test
    @DisplayName("rejects a token with no audience at all")
    void rejectsMissingAudience() {
        assertThat(validator.validate(tokenFor()).hasErrors()).isTrue();
    }
}
