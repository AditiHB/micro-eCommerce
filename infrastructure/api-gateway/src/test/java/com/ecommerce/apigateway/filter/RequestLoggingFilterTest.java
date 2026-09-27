package com.ecommerce.apigateway.filter;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;

import static org.assertj.core.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("RequestLoggingFilter Unit Tests")
class RequestLoggingFilterTest {

    @InjectMocks
    private RequestLoggingFilter filter;

    @BeforeEach
    void setUp() {
    }

    @Test
    @DisplayName("Should allow GET request to pass through")
    void testGetRequestPassthrough() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/test").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        GatewayFilter gatewayFilter = filter.apply(new RequestLoggingFilter.Config());
        gatewayFilter.filter(exchange, chain -> null);

        assertThat(exchange.getRequest().getMethod().toString()).isEqualTo("GET");
    }

    @Test
    @DisplayName("Should allow POST request to pass through")
    void testPostRequestPassthrough() {
        MockServerHttpRequest request = MockServerHttpRequest.post("/api/test").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        GatewayFilter gatewayFilter = filter.apply(new RequestLoggingFilter.Config());
        gatewayFilter.filter(exchange, chain -> null);

        assertThat(exchange.getRequest().getMethod().toString()).isEqualTo("POST");
    }

    @Test
    @DisplayName("Should allow PUT request to pass through")
    void testPutRequestPassthrough() {
        MockServerHttpRequest request = MockServerHttpRequest.put("/api/test").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        GatewayFilter gatewayFilter = filter.apply(new RequestLoggingFilter.Config());
        gatewayFilter.filter(exchange, chain -> null);

        assertThat(exchange.getRequest().getMethod().toString()).isEqualTo("PUT");
    }

    @Test
    @DisplayName("Should allow DELETE request to pass through")
    void testDeleteRequestPassthrough() {
        MockServerHttpRequest request = MockServerHttpRequest.delete("/api/test").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        GatewayFilter gatewayFilter = filter.apply(new RequestLoggingFilter.Config());
        gatewayFilter.filter(exchange, chain -> null);

        assertThat(exchange.getRequest().getMethod().toString()).isEqualTo("DELETE");
    }

    @Test
    @DisplayName("Should preserve request path")
    void testPreserveRequestPath() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/customers/123").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        GatewayFilter gatewayFilter = filter.apply(new RequestLoggingFilter.Config());
        gatewayFilter.filter(exchange, chain -> null);

        assertThat(exchange.getRequest().getURI().getPath()).isEqualTo("/api/customers/123");
    }

    @Test
    @DisplayName("Should preserve request headers")
    void testPreserveRequestHeaders() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/test")
            .header("Authorization", "Bearer token123")
            .header("Content-Type", "application/json")
            .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        GatewayFilter gatewayFilter = filter.apply(new RequestLoggingFilter.Config());
        gatewayFilter.filter(exchange, chain -> null);

        assertThat(exchange.getRequest().getHeaders().get("Authorization")).contains("Bearer token123");
        assertThat(exchange.getRequest().getHeaders().get("Content-Type")).contains("application/json");
    }
}
