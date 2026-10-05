package com.ecommerce.common.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("KeycloakJwtAuthenticationConverter")
class KeycloakJwtAuthenticationConverterTest {

    private final KeycloakJwtAuthenticationConverter converter = new KeycloakJwtAuthenticationConverter();

    private Jwt jwt(Map<String, Object> claims) {
        Jwt.Builder builder = Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60))
                .subject("user-id-123");
        claims.forEach(builder::claim);
        return builder.build();
    }

    @Test
    @DisplayName("maps realm roles to ROLE_ authorities, upper-cased")
    void mapsRealmRoles() {
        AbstractAuthenticationToken auth = converter.convert(jwt(Map.of(
                "realm_access", Map.of("roles", List.of("admin", "MANAGER", "offline_access")))));

        assertThat(auth.getAuthorities()).extracting(GrantedAuthority::getAuthority)
                .contains("ROLE_ADMIN", "ROLE_MANAGER", "ROLE_OFFLINE_ACCESS");
    }

    @Test
    @DisplayName("grants no authorities when the token has no realm_access claim")
    void noRealmAccess() {
        assertThat(converter.convert(jwt(Map.of())).getAuthorities()).isEmpty();
    }

    @Test
    @DisplayName("ignores a malformed roles claim instead of failing")
    void malformedRoles() {
        AbstractAuthenticationToken auth = converter.convert(jwt(Map.of(
                "realm_access", Map.of("roles", "ADMIN"))));

        assertThat(auth.getAuthorities()).isEmpty();
    }

    @Test
    @DisplayName("uses preferred_username as the principal name, falling back to the subject")
    void principalName() {
        assertThat(converter.convert(jwt(Map.of("preferred_username", "alice"))).getName()).isEqualTo("alice");
        assertThat(converter.convert(jwt(Map.of())).getName()).isEqualTo("user-id-123");
    }
}
