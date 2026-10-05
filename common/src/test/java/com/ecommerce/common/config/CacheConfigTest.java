package com.ecommerce.common.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.cache.Cache;
import org.springframework.cache.interceptor.CacheErrorHandler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;

@DisplayName("Cache policy")
class CacheConfigTest {

    @Test
    @DisplayName("only reference data is cached - never workflow state such as order, payment or stock")
    void onlyReferenceDataHasACache() {
        assertThat(CacheConfig.CUSTOMERS_CACHE).isEqualTo("customers");
        assertThat(CacheConfig.PRODUCTS_CACHE).isEqualTo("products");
        assertThat(CacheConfig.class.getDeclaredFields())
                .filteredOn(f -> f.getType() == String.class)
                .extracting(java.lang.reflect.Field::getName)
                .containsExactlyInAnyOrder("CUSTOMERS_CACHE", "PRODUCTS_CACHE");
    }

    @Test
    @DisplayName("a cache failure is a miss, never an error: reads, writes and evictions all degrade quietly")
    void cacheFailuresDegrade() {
        CacheErrorHandler handler = new RedisConfig().errorHandler();
        Cache cache = mock(Cache.class);
        RuntimeException redisDown = new RuntimeException("Unable to connect to Redis");

        assertThatCode(() -> handler.handleCacheGetError(redisDown, cache, "1")).doesNotThrowAnyException();
        assertThatCode(() -> handler.handleCachePutError(redisDown, cache, "1", "value")).doesNotThrowAnyException();
        assertThatCode(() -> handler.handleCacheEvictError(redisDown, cache, "1")).doesNotThrowAnyException();
        assertThatCode(() -> handler.handleCacheClearError(redisDown, cache)).doesNotThrowAnyException();
    }
}
