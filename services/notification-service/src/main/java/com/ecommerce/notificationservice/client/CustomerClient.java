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
 * Looks up a customer's contact details from the Customer Service so the
 * Notification Service knows who to notify and at what address.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class CustomerClient {

    @Qualifier("customerServiceRestTemplate")
    private final RestTemplate restTemplate;

    @Value("${services.customer.url:http://localhost:8081}")
    private String customerServiceUrl;

    @CircuitBreaker(name = "customerService", fallbackMethod = "fallbackGetCustomer")
    @Retry(name = "customerService")
    public Optional<CustomerInfo> getCustomer(Long customerId) {
        String url = customerServiceUrl + ApiConstants.API_PREFIX + ApiConstants.CUSTOMERS_ENDPOINT + "/" + customerId;
        log.debug("Looking up customer {} at {}", customerId, url);
        CustomerInfo customer = restTemplate.getForObject(url, CustomerInfo.class);
        return Optional.ofNullable(customer);
    }

    @SuppressWarnings("unused")
    private Optional<CustomerInfo> fallbackGetCustomer(Long customerId, Exception ex) {
        log.warn("Could not reach Customer Service for customer {}: {}", customerId, ex.getMessage());
        return Optional.empty();
    }
}
