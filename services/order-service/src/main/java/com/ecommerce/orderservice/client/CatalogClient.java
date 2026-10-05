package com.ecommerce.orderservice.client;

import com.ecommerce.common.client.BearerTokenRelayInterceptor;
import com.ecommerce.common.client.RestClients;
import com.ecommerce.common.constants.ApiConstants;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Asks the product catalogue what things cost. One batched call per order, so the time the order request spends
 * waiting on the catalogue is one round trip, not one per line.
 *
 * <p>This is a read, so it is safe to retry and to put a circuit breaker around (unlike creating the order, which
 * is a local write - see {@code docs/RESILIENCE.md}). The caller's own token is forwarded, so the catalogue applies
 * the caller's permissions.
 */
@Component
@Slf4j
public class CatalogClient {

    private final RestClient client;

    public CatalogClient(@Value("${services.product.url:http://localhost:8085}") String baseUrl,
                         @Value("${ecommerce.clients.connect-timeout:500ms}") Duration connectTimeout,
                         @Value("${ecommerce.clients.read-timeout:1500ms}") Duration readTimeout) {
        this.client = RestClients.builder(baseUrl, connectTimeout, readTimeout)
                .requestInterceptor(new BearerTokenRelayInterceptor())
                .build();
    }

    /** The catalogue entry for each SKU that exists, keyed by SKU; unknown SKUs are simply absent. */
    @Retry(name = "catalog")
    @CircuitBreaker(name = "catalog")
    public Map<String, ProductPrice> lookup(Collection<String> skus) {
        List<ProductPrice> found = client.get()
                .uri(uri -> uri.path(ApiConstants.API_PREFIX + ApiConstants.PRODUCTS_ENDPOINT + "/lookup")
                        .queryParam("skus", String.join(",", skus)).build())
                .retrieve()
                .body(new ParameterizedTypeReference<>() { });
        return found == null ? Map.of()
                : found.stream().collect(Collectors.toMap(ProductPrice::sku, Function.identity(), (a, b) -> a));
    }

    /** What the catalogue says one product costs right now. */
    public record ProductPrice(String sku, String name, BigDecimal price, String currency) {
    }
}
