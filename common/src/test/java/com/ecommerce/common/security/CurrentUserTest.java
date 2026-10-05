package com.ecommerce.common.security;

import com.ecommerce.common.exception.ResourceNotFoundException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("CurrentUser (object-level authorization)")
class CurrentUserTest {

    private CurrentUser currentUser;

    @BeforeEach
    void setUp() {
        SecurityProperties properties = new SecurityProperties();
        properties.setJwkSetUri("http://localhost/jwks");
        properties.setIssuer("http://issuer");
        properties.setAudience("ecommerce-api");
        currentUser = new CurrentUser(properties);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void authenticateAs(String role, Object customerIdClaim) {
        Jwt.Builder builder = Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60))
                .subject("someone");
        if (customerIdClaim != null) {
            builder.claim("customer_id", customerIdClaim);
        }
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(
                builder.build(), List.of(new SimpleGrantedAuthority("ROLE_" + role))));
    }

    @Test
    @DisplayName("a USER may access only the customer their token is bound to")
    void userOwnData() {
        authenticateAs(Roles.USER, "42");

        assertThat(currentUser.canAccessCustomer(42L)).isTrue();
        assertThat(currentUser.canAccessCustomer(43L)).isFalse();
        assertThat(currentUser.hasCrossCustomerAccess()).isFalse();
    }

    @Test
    @DisplayName("a USER whose token carries no customer id can access nothing")
    void userWithoutBinding() {
        authenticateAs(Roles.USER, null);

        assertThat(currentUser.customerId()).isEmpty();
        assertThat(currentUser.canAccessCustomer(1L)).isFalse();
    }

    @Test
    @DisplayName("a non-numeric customer id claim is treated as no binding rather than an error")
    void malformedClaim() {
        authenticateAs(Roles.USER, "not-a-number");

        assertThat(currentUser.customerId()).isEmpty();
        assertThat(currentUser.canAccessCustomer(1L)).isFalse();
    }

    @Test
    @DisplayName("ADMIN, MANAGER and SERVICE are not restricted to one customer")
    void backOfficeAndServices() {
        for (String role : List.of(Roles.ADMIN, Roles.MANAGER, Roles.SERVICE)) {
            authenticateAs(role, null);
            assertThat(currentUser.hasCrossCustomerAccess()).as(role).isTrue();
            assertThat(currentUser.canAccessCustomer(999L)).as(role).isTrue();
        }
    }

    @Test
    @DisplayName("an unauthenticated caller can access nothing")
    void anonymous() {
        assertThat(currentUser.hasCrossCustomerAccess()).isFalse();
        assertThat(currentUser.canAccessCustomer(1L)).isFalse();
    }

    @Test
    @DisplayName("requireAccessToCustomer hides other customers' records behind a not-found error")
    void requireAccess() {
        authenticateAs(Roles.USER, "7");

        assertThatCode(() -> currentUser.requireAccessToCustomer(7L, "Order", 100L)).doesNotThrowAnyException();
        assertThatThrownBy(() -> currentUser.requireAccessToCustomer(8L, "Order", 101L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Order not found with id: 101");
    }
}
