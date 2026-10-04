package com.ecommerce.apigateway.filter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("RateLimitingFilter Unit Tests")
class RateLimitingFilterTest {

    @Mock
    private RedisTemplate<String, String> redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOps;

    @InjectMocks
    private RateLimitingFilter filter;

    private RateLimitingFilter.Config config;

    @BeforeEach
    void setUp() {
        config = new RateLimitingFilter.Config();
        config.setRequestsPerMinute(10);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
    }

    @Test
    @DisplayName("Should allow request within rate limit")
    void testAllowRequestWithinLimit() {
        when(valueOps.increment(anyString())).thenReturn(6L);

        MockServerHttpRequest request = MockServerHttpRequest.get("/test").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        GatewayFilter gatewayFilter = filter.apply(config);
        gatewayFilter.filter(exchange, chain -> {
            assertThat(exchange.getResponse().getStatusCode()).isNotEqualTo(HttpStatus.TOO_MANY_REQUESTS);
            return null;
        });

        verify(valueOps).increment(anyString());
    }

    @Test
    @DisplayName("Should reject request exceeding rate limit")
    void testRejectRequestExceedingLimit() {
        when(valueOps.increment(anyString())).thenReturn(11L);

        MockServerHttpRequest request = MockServerHttpRequest.get("/test").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        GatewayFilter gatewayFilter = filter.apply(config);
        gatewayFilter.filter(exchange, chain -> null);

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
    }

    @Test
    @DisplayName("Should increment request count on every request, atomically")
    void testIncrementRequestCount() {
        when(valueOps.increment(anyString())).thenReturn(1L);

        MockServerHttpRequest request = MockServerHttpRequest.get("/test").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        GatewayFilter gatewayFilter = filter.apply(config);
        gatewayFilter.filter(exchange, chain -> null);

        verify(valueOps).increment(anyString());
        verify(redisTemplate).expire(anyString(), eq(60L), eq(TimeUnit.SECONDS));
    }

    @Test
    @DisplayName("Should not reset the window TTL on requests after the first")
    void testDoesNotRenewTtlAfterFirstRequest() {
        when(valueOps.increment(anyString())).thenReturn(2L);

        MockServerHttpRequest request = MockServerHttpRequest.get("/test").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        GatewayFilter gatewayFilter = filter.apply(config);
        gatewayFilter.filter(exchange, chain -> null);

        verify(redisTemplate, never()).expire(anyString(), anyLong(), any());
    }

    @Test
    @DisplayName("Should set rate limit headers")
    void testSetRateLimitHeaders() {
        when(valueOps.increment(anyString())).thenReturn(4L);

        MockServerHttpRequest request = MockServerHttpRequest.get("/test").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        GatewayFilter gatewayFilter = filter.apply(config);
        gatewayFilter.filter(exchange, chain -> null);

        assertThat(exchange.getResponse().getHeaders().get("X-RateLimit-Limit")).isNotEmpty();
        assertThat(exchange.getResponse().getHeaders().get("X-RateLimit-Remaining")).isNotEmpty();
    }

    @Test
    @DisplayName("Should use default rate limit when config not set")
    void testDefaultRateLimit() {
        config.setRequestsPerMinute(0);
        when(valueOps.increment(anyString())).thenReturn(1L);

        MockServerHttpRequest request = MockServerHttpRequest.get("/test").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        GatewayFilter gatewayFilter = filter.apply(config);
        gatewayFilter.filter(exchange, chain -> null);

        assertThat(exchange.getResponse().getStatusCode()).isNotEqualTo(HttpStatus.TOO_MANY_REQUESTS);
    }
}
