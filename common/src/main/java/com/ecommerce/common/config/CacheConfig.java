package com.ecommerce.common.config;

import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

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
