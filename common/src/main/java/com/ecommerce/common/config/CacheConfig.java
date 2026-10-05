package com.ecommerce.common.config;

/**
 * Names of the caches. There is exactly one cache implementation - the Redis-backed one in {@link RedisConfig},
 * shared by every replica of a service - so a write on one replica can never leave another serving stale data.
 *
 * <p>Only <em>reference data</em> is cached: things that are read far more than they change and that a slightly
 * stale read does not corrupt (a customer's profile, a catalogue entry). Workflow state - order status, payment
 * status, stock levels - is deliberately never cached: it changes on every saga step, so a cache would be
 * evicted constantly and, worse, could show a customer a stale status.
 */
public final class CacheConfig {

    public static final String CUSTOMERS_CACHE = "customers";
    public static final String PRODUCTS_CACHE = "products";

    private CacheConfig() {
    }
}
