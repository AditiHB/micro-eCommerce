package com.ecommerce.orderservice.client;

import com.ecommerce.common.client.BearerTokenRelayInterceptor;
import com.ecommerce.common.client.RestClients;
import com.ecommerce.common.constants.ApiConstants;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ssl.NoSuchSslBundleException;
import org.springframework.boot.ssl.SslBundle;
import org.springframework.boot.ssl.SslBundles;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.time.Duration;

/**
 * Checks that the customer an order is for exists. A read, so safe to retry; the caller's token is forwarded, so
 * a customer can only ever confirm their own record.
 */
@Component
public class CustomerDirectoryClient {

    private static final String MTLS_BUNDLE = "mtls";

    private final RestClient client;

    public CustomerDirectoryClient(@Value("${services.customer.url:http://localhost:8081}") String baseUrl,
                                   @Value("${ecommerce.clients.connect-timeout:500ms}") Duration connectTimeout,
                                   @Value("${ecommerce.clients.read-timeout:1500ms}") Duration readTimeout,
                                   SslBundles sslBundles) {
        SslBundle mtlsBundle = null;
        try {
            mtlsBundle = sslBundles.getBundle(MTLS_BUNDLE);
        } catch (NoSuchSslBundleException e) {
            // "mtls" profile not active (h2/postgres profiles) - call customer-service over plain HTTP.
        }
        this.client = RestClients.builder(baseUrl, connectTimeout, readTimeout, mtlsBundle)
                .requestInterceptor(new BearerTokenRelayInterceptor())
                .build();
    }

    @Retry(name = "customerDirectory")
    @CircuitBreaker(name = "customerDirectory")
    public boolean exists(Long customerId) {
        try {
            client.get()
                    .uri(ApiConstants.API_PREFIX + ApiConstants.CUSTOMERS_ENDPOINT + "/{id}", customerId)
                    .retrieve()
                    .toBodilessEntity();
            return true;
        } catch (HttpClientErrorException.NotFound e) {
            return false;
        }
    }
}
