package com.ecommerce.orderservice.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Order-service's own configuration: switches on {@link OrderProperties}. (The outbound HTTP clients are built
 * where they are used - see {@code CatalogClient} and {@code CustomerDirectoryClient} - each with explicit,
 * short timeouts; there is no shared, generously-timed HTTP client.)
 */
@Configuration
@EnableConfigurationProperties(OrderProperties.class)
public class RestClientConfig {
}
