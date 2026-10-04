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
            long maxRequests = config.requestsPerMinute > 0 ? config.requestsPerMinute : DEFAULT_REQUESTS_PER_MINUTE;

            // A single atomic INCR (not a separate GET then a separate INCR)
            // avoids a read-then-write race under real concurrent traffic:
            // a burst of requests could all read the same pre-increment
            // value before any of their increments landed, under-counting
            // the window. Only setting the TTL on the first request in the
            // window (count == 1) also fixes a second bug: the old code
            // reset the TTL on every single request, making the "60s
            // window" renew indefinitely under steady traffic instead of
            // being a real fixed window.
            //
            // (This filter not running at all for most routes - the bug
            // that made the above two issues hard to even notice - was a
            // separate problem: GatewayConfiguration used to define a
            // second, filter-less RouteLocator bean whose routes shared the
            // same IDs as these and were winning the match for
            // order/customer/inventory/payment-service, bypassing this
            // filter and the circuit breaker entirely. See git history on
            // GatewayConfiguration.java.)
            long currentRequests = redisTemplate.opsForValue().increment(rateLimitKey);
            if (currentRequests == 1) {
                redisTemplate.expire(rateLimitKey, 60, TimeUnit.SECONDS);
            }

            if (currentRequests > maxRequests) {
                log.warn("Rate limit exceeded for client: {}", clientId);
                exchange.getResponse().setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
                return exchange.getResponse().setComplete();
            }

            addResponseHeaders(exchange, currentRequests, maxRequests);

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
