package com.ecommerce.apigateway.filter;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * RequestLoggingFilter is a plain GlobalFilter (not a gateway filter factory), so it has no
 * nested Config class or apply(Config) method - it's exercised directly via filter(exchange, chain).
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("RequestLoggingFilter Unit Tests")
class RequestLoggingFilterTest {

    private final RequestLoggingFilter filter = new RequestLoggingFilter();

    @Mock
    private GatewayFilterChain chain;

    @Test
    @DisplayName("Should allow GET request to pass through")
    void testGetRequestPassthrough() {
        when(chain.filter(any())).thenReturn(Mono.empty());
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/test").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        filter.filter(exchange, chain).block();

        assertThat(exchange.getRequest().getMethod().toString()).isEqualTo("GET");
    }

    @Test
    @DisplayName("Should allow POST request to pass through")
    void testPostRequestPassthrough() {
        when(chain.filter(any())).thenReturn(Mono.empty());
        MockServerHttpRequest request = MockServerHttpRequest.post("/api/test").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        filter.filter(exchange, chain).block();

        assertThat(exchange.getRequest().getMethod().toString()).isEqualTo("POST");
    }

    @Test
    @DisplayName("Should allow PUT request to pass through")
    void testPutRequestPassthrough() {
        when(chain.filter(any())).thenReturn(Mono.empty());
        MockServerHttpRequest request = MockServerHttpRequest.put("/api/test").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        filter.filter(exchange, chain).block();

        assertThat(exchange.getRequest().getMethod().toString()).isEqualTo("PUT");
    }

    @Test
    @DisplayName("Should allow DELETE request to pass through")
    void testDeleteRequestPassthrough() {
        when(chain.filter(any())).thenReturn(Mono.empty());
        MockServerHttpRequest request = MockServerHttpRequest.delete("/api/test").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        filter.filter(exchange, chain).block();

        assertThat(exchange.getRequest().getMethod().toString()).isEqualTo("DELETE");
    }

    @Test
    @DisplayName("Should preserve request path")
    void testPreserveRequestPath() {
        when(chain.filter(any())).thenReturn(Mono.empty());
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/customers/123").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        filter.filter(exchange, chain).block();

        assertThat(exchange.getRequest().getURI().getPath()).isEqualTo("/api/customers/123");
    }

    @Test
    @DisplayName("Should preserve request headers")
    void testPreserveRequestHeaders() {
        when(chain.filter(any())).thenReturn(Mono.empty());
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/test")
            .header("Authorization", "Bearer token123")
            .header("Content-Type", "application/json")
            .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        filter.filter(exchange, chain).block();

        assertThat(exchange.getRequest().getHeaders().get("Authorization")).contains("Bearer token123");
        assertThat(exchange.getRequest().getHeaders().get("Content-Type")).contains("application/json");
    }
}
