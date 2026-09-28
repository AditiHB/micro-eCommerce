package com.ecommerce.apigateway.filter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AuthenticationFilter Unit Tests")
class AuthenticationFilterTest {

    @InjectMocks
    private AuthenticationFilter filter;

    private AuthenticationFilter.Config config;

    @BeforeEach
    void setUp() {
        config = new AuthenticationFilter.Config();
        ReflectionTestUtils.setField(filter, "jwtSecret", "mySecretKeyForJWTTokenSigningPurposeOnly12345678901234567890");
    }

    @Test
    @DisplayName("Should allow request to public path without token")
    void testPublicPathNoAuth() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/auth/login").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        GatewayFilter gatewayFilter = filter.apply(config);
        gatewayFilter.filter(exchange, chain -> null);

        assertThat(exchange.getResponse().getStatusCode()).isNotEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("Should allow request to health endpoint")
    void testHealthEndpointNoAuth() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/customers/health").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        GatewayFilter gatewayFilter = filter.apply(config);
        gatewayFilter.filter(exchange, chain -> null);

        assertThat(exchange.getResponse().getStatusCode()).isNotEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("Should allow request to actuator endpoints")
    void testActuatorNoAuth() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/actuator").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        GatewayFilter gatewayFilter = filter.apply(config);
        gatewayFilter.filter(exchange, chain -> null);

        assertThat(exchange.getResponse().getStatusCode()).isNotEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("Should reject protected endpoint without token")
    void testProtectedEndpointNoAuth() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/protected").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        GatewayFilter gatewayFilter = filter.apply(config);
        gatewayFilter.filter(exchange, chain -> null);

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("Should reject protected endpoint with invalid Bearer format")
    void testProtectedEndpointInvalidBearer() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/protected")
            .header("Authorization", "InvalidToken")
            .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        GatewayFilter gatewayFilter = filter.apply(config);
        gatewayFilter.filter(exchange, chain -> null);

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("Should reject protected endpoint with empty Bearer token")
    void testProtectedEndpointEmptyBearer() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/protected")
            .header("Authorization", "Bearer ")
            .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        GatewayFilter gatewayFilter = filter.apply(config);
        gatewayFilter.filter(exchange, chain -> null);

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("Should allow register endpoint without auth")
    void testRegisterEndpointNoAuth() {
        MockServerHttpRequest request = MockServerHttpRequest.post("/api/auth/register").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        GatewayFilter gatewayFilter = filter.apply(config);
        gatewayFilter.filter(exchange, chain -> null);

        assertThat(exchange.getResponse().getStatusCode()).isNotEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
