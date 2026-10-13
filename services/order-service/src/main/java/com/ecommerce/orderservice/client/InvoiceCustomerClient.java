package com.ecommerce.orderservice.client;

import com.ecommerce.common.client.RestClients;
import com.ecommerce.common.constants.ApiConstants;
import com.ecommerce.orderservice.config.ServiceAccountAuthInterceptor;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ssl.NoSuchSslBundleException;
import org.springframework.boot.ssl.SslBundle;
import org.springframework.boot.ssl.SslBundles;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.Optional;

/**
 * Looks up a customer's name and email for the invoice batch job. Unlike the request-scoped
 * {@code CustomerDirectoryClient} (which relays the caller's own bearer token), this call has no caller -
 * it runs on a schedule - so it authenticates as this service's own client-credentials identity (see
 * {@link com.ecommerce.orderservice.config.ServiceAuthConfig}).
 */
@Component
@Slf4j
public class InvoiceCustomerClient {

    private static final String MTLS_BUNDLE = "mtls";

    private final RestClient client;

    public InvoiceCustomerClient(@Value("${services.customer.url:http://localhost:8081}") String baseUrl,
                                 @Value("${ecommerce.clients.connect-timeout:500ms}") Duration connectTimeout,
                                 @Value("${ecommerce.clients.read-timeout:1500ms}") Duration readTimeout,
                                 ServiceAccountAuthInterceptor serviceAccountAuth,
                                 SslBundles sslBundles) {
        SslBundle mtlsBundle = null;
        try {
            mtlsBundle = sslBundles.getBundle(MTLS_BUNDLE);
        } catch (NoSuchSslBundleException e) {
            // "mtls" profile not active (h2/postgres profiles) - call customer-service over plain HTTP.
        }
        this.client = RestClients.builder(baseUrl, connectTimeout, readTimeout, mtlsBundle)
                .requestInterceptor(serviceAccountAuth)
                .build();
    }

    @Retry(name = "customerDirectory")
    @CircuitBreaker(name = "customerDirectory")
    public Optional<CustomerInfo> find(Long customerId) {
        log.debug("Looking up customer {} for an invoice", customerId);
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
