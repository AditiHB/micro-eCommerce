package com.ecommerce.notificationservice.client;

import com.ecommerce.common.client.RestClients;
import com.ecommerce.common.constants.ApiConstants;
import com.ecommerce.notificationservice.config.ServiceAccountAuthInterceptor;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.Optional;

/**
 * Looks up a customer's contact details so a notification knows who to address and where.
 *
 * <p>A <em>missing</em> customer (404) is an answer: empty. A customer service that is down or too slow is NOT
 * an answer, so the failure propagates (after a short retry) to the event consumer, which retries with backoff and
 * finally dead-letters the event. The old behaviour - turning every failure into "no customer" and moving on -
 * silently lost the notification.
 *
 * <p>The call authenticates as this service's own client-credentials identity (the token is cached by the
 * interceptor). It is a read, so safe to retry; it has a short explicit timeout and a circuit breaker.
 */
@Component
@Slf4j
public class CustomerClient {

    private final RestClient client;

    public CustomerClient(@Value("${services.customer.url:http://localhost:8081}") String baseUrl,
                          @Value("${ecommerce.clients.connect-timeout:500ms}") Duration connectTimeout,
                          @Value("${ecommerce.clients.read-timeout:1500ms}") Duration readTimeout,
                          ServiceAccountAuthInterceptor serviceAccountAuth) {
        this.client = RestClients.builder(baseUrl, connectTimeout, readTimeout)
                .requestInterceptor(serviceAccountAuth)
                .build();
    }

    @Retry(name = "customerService")
    @CircuitBreaker(name = "customerService")
    public Optional<CustomerInfo> find(Long customerId) {
        log.debug("Looking up customer {}", customerId);
        try {
            return Optional.ofNullable(client.get()
                    .uri(ApiConstants.API_PREFIX + ApiConstants.CUSTOMERS_ENDPOINT + "/{id}", customerId)
                    .retrieve()
                    .body(CustomerInfo.class));
        } catch (HttpClientErrorException.NotFound e) {
            return Optional.empty();
        }
    }
}
