package com.ecommerce.notificationservice.client;

import com.ecommerce.common.constants.ApiConstants;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
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

    private final RestTemplate restTemplate;

    public OrderClient(@Qualifier("orderServiceRestTemplate") RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    @Value("${services.order.url:http://localhost:8083}")
    private String orderServiceUrl;

    @CircuitBreaker(name = "orderService", fallbackMethod = "fallbackGetOrder")
    @Retry(name = "orderService")
    public Optional<OrderInfo> getOrder(Long orderId) {
        String url = orderServiceUrl + ApiConstants.API_PREFIX + ApiConstants.ORDERS_ENDPOINT + "/" + orderId;
        log.debug("Looking up order {} at {}", orderId, url);
        OrderInfo order = restTemplate.getForObject(url, OrderInfo.class);
        return Optional.ofNullable(order);
    }

    @SuppressWarnings("unused")
    private Optional<OrderInfo> fallbackGetOrder(Long orderId, Exception ex) {
        log.warn("Could not reach Order Service for order {}: {}", orderId, ex.getMessage());
        return Optional.empty();
    }
}
