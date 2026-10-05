package com.ecommerce.inventoryservice.config;

import org.springframework.context.annotation.Configuration;

/**
 * Intentionally empty: inventory-service makes no outbound HTTP calls. (It used to declare a shared RestTemplate with
 * a 30-second read timeout that nothing used; calls to other services are built where they are made, with short
 * explicit timeouts - see docs/RESILIENCE.md.)
 */
@Configuration
public class RestClientConfig {
}
