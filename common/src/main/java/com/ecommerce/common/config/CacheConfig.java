package com.ecommerce.common.config;

import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Local, per-instance, in-memory cache ({@link ConcurrentMapCacheManager}) -
 * NOT shared across replicas. Fine for a single-instance service; confirmed
 * live to silently serve stale reads the moment a service runs more than one
 * replica (see {@link RedisConfig}'s javadoc for how that was found and
 * fixed). customer/order/inventory/payment-service import {@link
 * RedisConfig} instead for exactly that reason - this class is still used by
 * notification-service and product-service.
 *
 * <p>Note: notification-service's {@code CommonIntegrationConfig} doesn't
 * import this class (or RedisConfig) at all, and its {@code @SpringBootApplication}
 * doesn't component-scan {@code com.ecommerce.common.config} either - so no
 * {@code CacheManager} bean exists in its context and {@code @EnableCaching}
 * never activates there. Its own {@code @Cacheable}/{@code @CacheEvict}
 * annotations (referencing this class's cache-name constants) are therefore
 * currently inert, not merely local - a separate, narrower gap than the one
 * above, left as-is here since notification-service was never part of this
 * investigation's replica-consistency testing.
 */
@Configuration
@EnableCaching
public class CacheConfig {

    public static final String CUSTOMERS_CACHE = "customers";
    public static final String ORDERS_CACHE = "orders";
    public static final String INVENTORY_CACHE = "inventory";
    public static final String PAYMENTS_CACHE = "payments";
    public static final String USERS_CACHE = "users";
    public static final String NOTIFICATIONS_CACHE = "notifications";

    @Bean
    public CacheManager cacheManager() {
        return new ConcurrentMapCacheManager(
                CUSTOMERS_CACHE,
                ORDERS_CACHE,
                INVENTORY_CACHE,
                PAYMENTS_CACHE,
                USERS_CACHE,
                NOTIFICATIONS_CACHE
        );
    }
}
