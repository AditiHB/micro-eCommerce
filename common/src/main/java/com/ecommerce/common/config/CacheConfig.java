package com.ecommerce.common.config;

import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * TODO (known gap): this CacheManager is a local, per-instance, in-memory
 * cache ({@link ConcurrentMapCacheManager}) - NOT shared across service
 * replicas, even though every service's application.yml configures
 * {@code spring.cache.type: redis} and a real Redis connection. {@link
 * RedisConfig} already defines a proper distributed RedisCacheManager for
 * this, but nothing imports it - every service imports this class instead.
 * See RedisConfig's javadoc for the fix.
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
