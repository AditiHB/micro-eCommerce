package com.ecommerce.apigateway.filter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;

import java.util.concurrent.TimeUnit;

@Component
@Slf4j
public class RateLimitingFilter extends AbstractGatewayFilterFactory<RateLimitingFilter.Config> {

    private static final String RATE_LIMIT_KEY_PREFIX = "rate_limit:";
    private static final long DEFAULT_REQUESTS_PER_MINUTE = 100;

    private final RedisTemplate<String, String> redisTemplate;

    public RateLimitingFilter(RedisTemplate<String, String> redisTemplate) {
        super(Config.class);
        this.redisTemplate = redisTemplate;
    }

    @Override
    public GatewayFilter apply(Config config) {
        return (exchange, chain) -> {
            String clientId = getClientIdentifier(exchange);
            String rateLimitKey = RATE_LIMIT_KEY_PREFIX + clientId;

            long currentRequests = getCurrentRequestCount(rateLimitKey);
            long maxRequests = config.requestsPerMinute > 0 ? config.requestsPerMinute : DEFAULT_REQUESTS_PER_MINUTE;

            if (currentRequests >= maxRequests) {
                log.warn("Rate limit exceeded for client: {}", clientId);
                exchange.getResponse().setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
                return exchange.getResponse().setComplete();
            }

            incrementRequestCount(rateLimitKey, 60);
            addResponseHeaders(exchange, currentRequests + 1, maxRequests);

            return chain.filter(exchange);
        };
    }

    private String getClientIdentifier(ServerWebExchange exchange) {
        String clientIp = exchange.getRequest().getHeaders().getFirst("X-Forwarded-For");
        if (clientIp == null || clientIp.isEmpty()) {
            clientIp = exchange.getRequest().getRemoteAddress() != null
                ? exchange.getRequest().getRemoteAddress().getAddress().getHostAddress()
                : "unknown";
        }
        return clientIp;
    }

    private long getCurrentRequestCount(String key) {
        String value = redisTemplate.opsForValue().get(key);
        return value != null ? Long.parseLong(value) : 0;
    }

    private void incrementRequestCount(String key, long ttlSeconds) {
        redisTemplate.opsForValue().increment(key);
        redisTemplate.expire(key, ttlSeconds, TimeUnit.SECONDS);
    }

    private void addResponseHeaders(ServerWebExchange exchange, long currentRequests, long maxRequests) {
        exchange.getResponse().getHeaders().add("X-RateLimit-Limit", String.valueOf(maxRequests));
        exchange.getResponse().getHeaders().add("X-RateLimit-Remaining", String.valueOf(Math.max(0, maxRequests - currentRequests)));
    }

    public static class Config {
        public long requestsPerMinute = DEFAULT_REQUESTS_PER_MINUTE;

        public long getRequestsPerMinute() {
            return requestsPerMinute;
        }

        public void setRequestsPerMinute(long requestsPerMinute) {
            this.requestsPerMinute = requestsPerMinute;
        }
    }
}
