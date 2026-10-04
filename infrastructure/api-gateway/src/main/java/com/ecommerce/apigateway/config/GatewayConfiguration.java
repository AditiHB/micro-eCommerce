package com.ecommerce.apigateway.config;

/**
 * This class used to define a second, programmatic {@code RouteLocator} bean
 * with routes for order/customer/inventory/payment-service sharing the EXACT
 * SAME route IDs as the fully-configured routes in application.yml - but
 * with NO filters attached (no CircuitBreaker, no RateLimitingFilter, no
 * AuthenticationFilter). It was never removed after the YAML-based routes
 * (with filters) were added, probably left over from an earlier draft.
 *
 * Spring Cloud Gateway does not deduplicate routes by ID across different
 * RouteLocator sources: both this bean's filter-less routes AND the YAML
 * ones existed side by side, and this one's predicate matches were winning
 * for order/customer/inventory/payment-service, silently bypassing the
 * gateway's circuit breakers and rate limiting for those 4 routes entirely
 * (auth-service was unaffected only because this class never defined a
 * route for it). Requests still appeared to work normally and still
 * enforced auth, because each backend service independently validates its
 * own JWT (see common.security.SecurityConfig) regardless of what the
 * gateway does - so the gap was invisible from the outside; it only showed
 * up as "RateLimitingFilter never logs/increments Redis, CircuitBreaker
 * never registers in the Resilience4j registry" for those 4 routes,
 * confirmed via /actuator/circuitbreakers and redis-cli MONITOR while
 * building e2e-tests/resilience.feature.
 */
public class GatewayConfiguration {
}
