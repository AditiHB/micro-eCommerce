package com.ecommerce.notificationservice.client;

import com.ecommerce.common.constants.ApiConstants;
import com.ecommerce.common.security.JwtTokenProvider;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Optional;

/**
 * Looks up order details from Order Service. Needed because payment events
 * (payment-processed / payment-failed) only carry the orderId, not the
 * customerId, so Notification Service resolves the owning customer itself.
 */
@Component
@Slf4j
public class OrderClient {

    /**
     * GET /api/orders/** requires an authenticated USER/ADMIN/MANAGER (see
     * common.security.SecurityConfig) - this call must carry a valid JWT the
     * same as any other client would, or Order Service 401s it. The JWT is
     * validated by loading this username from Order Service's own local
     * users table, so it must be seeded there too (see order-service's
     * V7__Seed_Service_Account.sql).
     */
    private static final String SERVICE_ACCOUNT_USERNAME = "notification-service-account";

    private final RestTemplate restTemplate;
    private final JwtTokenProvider tokenProvider;

    public OrderClient(@Qualifier("orderServiceRestTemplate") RestTemplate restTemplate,
                        JwtTokenProvider tokenProvider) {
        this.restTemplate = restTemplate;
        this.tokenProvider = tokenProvider;
    }

    @Value("${services.order.url:http://localhost:8083}")
    private String orderServiceUrl;

    @CircuitBreaker(name = "orderService", fallbackMethod = "fallbackGetOrder")
    @Retry(name = "orderService")
    public Optional<OrderInfo> getOrder(Long orderId) {
        String url = orderServiceUrl + ApiConstants.API_PREFIX + ApiConstants.ORDERS_ENDPOINT + "/" + orderId;
        log.debug("Looking up order {} at {}", orderId, url);
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(tokenProvider.generateTokenFromUsername(SERVICE_ACCOUNT_USERNAME));
        OrderInfo order = restTemplate.exchange(url, HttpMethod.GET, new HttpEntity<>(headers), OrderInfo.class)
            .getBody();
        return Optional.ofNullable(order);
    }

    @SuppressWarnings("unused")
    private Optional<OrderInfo> fallbackGetOrder(Long orderId, Exception ex) {
        log.warn("Could not reach Order Service for order {}: {}", orderId, ex.getMessage());
        return Optional.empty();
    }
}
