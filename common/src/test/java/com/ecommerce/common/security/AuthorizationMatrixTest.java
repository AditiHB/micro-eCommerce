package com.ecommerce.common.security;

import com.ecommerce.common.exception.SecurityExceptionAdvice;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.TestFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;

/**
 * The authorization matrix, asserted exhaustively: for every endpoint and every kind of caller the
 * expected outcome is spelled out here, so loosening a rule - or adding an endpoint nobody
 * remembered to protect - fails a test instead of shipping.
 *
 * <p>This is the test that was missing when {@code PUT /api/v1/inventory}, refunds and the whole
 * product API were reachable by any authenticated user. The controllers' own slice tests run with
 * {@code addFilters = false} and therefore cannot see any of this.
 */
@WebMvcTest(controllers = AuthorizationMatrixTest.AnyEndpoint.class)
@Import({SecurityConfig.class, SecurityExceptionAdvice.class, CurrentUser.class, AuthorizationMatrixTest.AnyEndpoint.class})
@TestPropertySource(properties = {
        "ecommerce.security.jwk-set-uri=http://localhost:0/jwks",
        "ecommerce.security.issuer=http://issuer.test/realms/ecommerce",
        "ecommerce.security.audience=ecommerce-api"
})
@DisplayName("Authorization matrix")
class AuthorizationMatrixTest {

    private static final Set<String> ANYONE_AUTHENTICATED =
            Set.of(Roles.USER, Roles.MANAGER, Roles.ADMIN, Roles.SERVICE);
    private static final Set<String> BACK_OFFICE = Set.of(Roles.MANAGER, Roles.ADMIN);
    private static final Set<String> ADMIN_ONLY = Set.of(Roles.ADMIN);
    private static final Set<String> NOBODY = Set.of();

    private static final List<String> CALLER_ROLES =
            List.of(Roles.USER, Roles.MANAGER, Roles.ADMIN, Roles.SERVICE);

    @SpringBootConfiguration
    static class TestApplication {
    }

    /** Answers 200 to anything the security layer lets through. */
    @RestController
    static class AnyEndpoint {
        @RequestMapping("/**")
        String ok() {
            return "ok";
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private JwtDecoder jwtDecoder;

    private record Rule(HttpMethod method, String path, Set<String> allowed) {
    }

    private static List<Rule> rules() {
        return List.of(
                // customer-service
                new Rule(HttpMethod.GET, "/api/v1/customers", BACK_OFFICE),
                new Rule(HttpMethod.GET, "/api/v1/customers/1", ANYONE_AUTHENTICATED),
                new Rule(HttpMethod.POST, "/api/v1/customers", BACK_OFFICE),
                new Rule(HttpMethod.PUT, "/api/v1/customers/1", BACK_OFFICE),
                new Rule(HttpMethod.DELETE, "/api/v1/customers/1", ADMIN_ONLY),
                // order-service
                new Rule(HttpMethod.GET, "/api/v1/orders", ANYONE_AUTHENTICATED),
                new Rule(HttpMethod.GET, "/api/v1/orders/1", ANYONE_AUTHENTICATED),
                new Rule(HttpMethod.POST, "/api/v1/orders", Set.of(Roles.USER, Roles.MANAGER, Roles.ADMIN)),
                new Rule(HttpMethod.POST, "/api/v1/orders/1/cancel", Set.of(Roles.USER, Roles.MANAGER, Roles.ADMIN)),
                new Rule(HttpMethod.PUT, "/api/v1/orders/1/status", BACK_OFFICE),
                new Rule(HttpMethod.DELETE, "/api/v1/orders/1", NOBODY),
                // payment-service: money only moves through back-office or the saga
                new Rule(HttpMethod.GET, "/api/v1/payments", BACK_OFFICE),
                new Rule(HttpMethod.GET, "/api/v1/payments/1", BACK_OFFICE),
                new Rule(HttpMethod.GET, "/api/v1/payments/order/1", BACK_OFFICE),
                new Rule(HttpMethod.POST, "/api/v1/payments/1/refund", BACK_OFFICE),
                // inventory-service
                new Rule(HttpMethod.GET, "/api/v1/inventory", BACK_OFFICE),
                new Rule(HttpMethod.GET, "/api/v1/inventory/1", BACK_OFFICE),
                new Rule(HttpMethod.POST, "/api/v1/inventory", ADMIN_ONLY),
                new Rule(HttpMethod.POST, "/api/v1/inventory/1/reserve", ADMIN_ONLY),
                new Rule(HttpMethod.POST, "/api/v1/inventory/1/release", ADMIN_ONLY),
                new Rule(HttpMethod.PUT, "/api/v1/inventory/1", BACK_OFFICE),
                // the product catalogue (served by inventory-service)
                new Rule(HttpMethod.GET, "/api/v1/products", ANYONE_AUTHENTICATED),
                new Rule(HttpMethod.GET, "/api/v1/products/sku/ABC", ANYONE_AUTHENTICATED),
                new Rule(HttpMethod.POST, "/api/v1/products", BACK_OFFICE),
                new Rule(HttpMethod.PUT, "/api/v1/products/1", BACK_OFFICE),
                new Rule(HttpMethod.DELETE, "/api/v1/products/1", BACK_OFFICE),
                // notification-service
                new Rule(HttpMethod.GET, "/api/v1/notifications", BACK_OFFICE),
                new Rule(HttpMethod.GET, "/api/v1/notifications/customer/1", BACK_OFFICE),
                // operator tooling: every service exposes the dead letters of its own consumers
                new Rule(HttpMethod.GET, "/api/v1/dead-letters", ADMIN_ONLY),
                new Rule(HttpMethod.POST, "/api/v1/dead-letters/1/replay", ADMIN_ONLY),
                // the unversioned API is only ever served through the gateway's rewrite to /api/v1
                new Rule(HttpMethod.GET, "/api/orders", NOBODY),
                new Rule(HttpMethod.GET, "/api/customers/1", NOBODY),
                new Rule(HttpMethod.POST, "/api/payments/1/refund", NOBODY),
                // endpoints that no longer exist, or never should: denied to everyone (deny by default)
                new Rule(HttpMethod.POST, "/api/v1/auth/login", NOBODY),
                new Rule(HttpMethod.GET, "/api/v1/auth/me", NOBODY),
                new Rule(HttpMethod.GET, "/api/v1/unknown", NOBODY),
                new Rule(HttpMethod.GET, "/actuator/prometheus", NOBODY),
                new Rule(HttpMethod.GET, "/actuator/env", NOBODY),
                new Rule(HttpMethod.GET, "/swagger-ui/index.html", NOBODY),
                new Rule(HttpMethod.GET, "/v3/api-docs", NOBODY)
        );
    }

    @TestFactory
    @DisplayName("Every endpoint answers every kind of caller as specified")
    Stream<DynamicTest> matrix() {
        List<DynamicTest> tests = new ArrayList<>();
        for (Rule rule : rules()) {
            // Anonymous callers are always turned away with 401.
            tests.add(DynamicTest.dynamicTest(rule.method() + " " + rule.path() + " as anonymous -> 401",
                    () -> assertThat(status(rule, null)).isEqualTo(401)));
            for (String role : CALLER_ROLES) {
                boolean allowed = rule.allowed().contains(role);
                tests.add(DynamicTest.dynamicTest(
                        rule.method() + " " + rule.path() + " as " + role + " -> " + (allowed ? "allowed" : "403"),
                        () -> {
                            int status = status(rule, jwt().authorities(new SimpleGrantedAuthority("ROLE_" + role)));
                            if (allowed) {
                                assertThat(status).isEqualTo(200);
                            } else {
                                assertThat(status).isEqualTo(403);
                            }
                        }));
            }
        }
        return tests.stream();
    }

    @TestFactory
    @DisplayName("Liveness and readiness probes are public, everything else under /actuator is not")
    Stream<DynamicTest> probesArePublic() {
        return Stream.of("/actuator/health", "/actuator/health/liveness", "/actuator/health/readiness", "/actuator/info")
                .map(path -> DynamicTest.dynamicTest(path + " as anonymous -> 200",
                        () -> assertThat(status(new Rule(HttpMethod.GET, path, NOBODY), null)).isEqualTo(200)));
    }

    @TestFactory
    @DisplayName("A token with no recognised role gets nothing, even on the endpoints open to every role")
    Stream<DynamicTest> roleLessTokenGetsNothing() {
        return Stream.of("/api/v1/orders", "/api/v1/customers/1", "/api/v1/products")
                .map(path -> DynamicTest.dynamicTest(path + " with no roles -> 403",
                        () -> assertThat(status(new Rule(HttpMethod.GET, path, NOBODY), jwt())).isEqualTo(403)));
    }

    private int status(Rule rule, RequestPostProcessor auth) throws Exception {
        var builder = request(rule.method(), rule.path());
        if (auth != null) {
            builder = builder.with(auth);
        }
        return mockMvc.perform(builder).andReturn().getResponse().getStatus();
    }
}
