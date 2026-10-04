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
 * Looks up a customer's contact details from the Customer Service so the
 * Notification Service knows who to notify and at what address.
 */
@Component
@Slf4j
public class CustomerClient {

    /**
     * GET /api/customers/** requires an authenticated USER/ADMIN/MANAGER
     * (see common.security.SecurityConfig) - this call must carry a valid
     * JWT the same as any other client would, or Customer Service 401s it.
     * The JWT is validated by loading this username from Customer Service's
     * own local users table, so it must be seeded there too (see
     * customer-service's V7__Seed_Service_Account.sql).
     */
    private static final String SERVICE_ACCOUNT_USERNAME = "notification-service-account";

    private final RestTemplate restTemplate;
    private final JwtTokenProvider tokenProvider;

    public CustomerClient(@Qualifier("customerServiceRestTemplate") RestTemplate restTemplate,
                           JwtTokenProvider tokenProvider) {
        this.restTemplate = restTemplate;
        this.tokenProvider = tokenProvider;
    }

    @Value("${services.customer.url:http://localhost:8081}")
    private String customerServiceUrl;

    @CircuitBreaker(name = "customerService", fallbackMethod = "fallbackGetCustomer")
    @Retry(name = "customerService")
    public Optional<CustomerInfo> getCustomer(Long customerId) {
        String url = customerServiceUrl + ApiConstants.API_PREFIX + ApiConstants.CUSTOMERS_ENDPOINT + "/" + customerId;
        log.debug("Looking up customer {} at {}", customerId, url);
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(tokenProvider.generateTokenFromUsername(SERVICE_ACCOUNT_USERNAME));
        CustomerInfo customer = restTemplate.exchange(url, HttpMethod.GET, new HttpEntity<>(headers), CustomerInfo.class)
            .getBody();
        return Optional.ofNullable(customer);
    }

    @SuppressWarnings("unused")
    private Optional<CustomerInfo> fallbackGetCustomer(Long customerId, Exception ex) {
        log.warn("Could not reach Customer Service for customer {}: {}", customerId, ex.getMessage());
        return Optional.empty();
    }
}
