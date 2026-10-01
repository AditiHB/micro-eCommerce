package com.ecommerce.apigateway.filter;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;

import javax.crypto.SecretKey;
import java.util.Arrays;
import java.util.List;

@Component
@Slf4j
public class AuthenticationFilter extends AbstractGatewayFilterFactory<AuthenticationFilter.Config> {

    @Value("${jwt.secret}")
    private String jwtSecret;

    private static final List<String> PUBLIC_PATHS = Arrays.asList(
        "/api/auth/login",
        "/api/auth/register",
        "/api/customers/health",
        "/api/orders/health",
        "/api/inventory/health",
        "/api/payments/health",
        "/actuator"
    );

    public AuthenticationFilter() {
        super(Config.class);
    }

    @Override
    public GatewayFilter apply(Config config) {
        return (exchange, chain) -> {
            String path = exchange.getRequest().getURI().getPath();

            if (isPublicPath(path)) {
                return chain.filter(exchange);
            }

            String authHeader = exchange.getRequest().getHeaders().getFirst("Authorization");

            if (authHeader == null || authHeader.isEmpty()) {
                log.warn("Missing authorization header for path: {}", path);
                exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
                return exchange.getResponse().setComplete();
            }

            try {
                String token = extractToken(authHeader);
                validateToken(token);
                String username = getUsernameFromToken(token);

                exchange.getRequest().mutate()
                    .header("X-User-Id", username)
                    .header("X-Trace-Id", exchange.getRequest().getHeaders().getFirst("X-Trace-Id") != null
                        ? exchange.getRequest().getHeaders().getFirst("X-Trace-Id")
                        : generateTraceId())
                    .build();

                return chain.filter(exchange);
            } catch (Exception ex) {
                log.warn("JWT validation failed: {}", ex.getMessage());
                exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
                return exchange.getResponse().setComplete();
            }
        };
    }

    private boolean isPublicPath(String path) {
        return PUBLIC_PATHS.stream().anyMatch(path::startsWith);
    }

    private String extractToken(String authHeader) {
        if (authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7);
        }
        throw new IllegalArgumentException("Invalid authorization header format");
    }

    private void validateToken(String token) {
        try {
            SecretKey key = Keys.hmacShaKeyFor(jwtSecret.getBytes());
            Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token);
        } catch (Exception ex) {
            log.error("Token validation failed: {}", ex.getMessage());
            throw ex;
        }
    }

    private String getUsernameFromToken(String token) {
        try {
            SecretKey key = Keys.hmacShaKeyFor(jwtSecret.getBytes());
            Claims claims = Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
            return claims.getSubject();
        } catch (Exception ex) {
            log.error("Failed to extract username from token: {}", ex.getMessage());
            throw ex;
        }
    }

    private String generateTraceId() {
        return "trace-" + System.currentTimeMillis() + "-" + Thread.currentThread().getId();
    }

    public static class Config {
    }
}
