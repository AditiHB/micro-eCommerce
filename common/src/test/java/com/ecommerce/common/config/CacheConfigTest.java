package com.ecommerce.common.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@DisplayName("CacheConfig Integration Tests")
class CacheConfigTest {

    @Autowired(required = false)
    private CacheManager cacheManager;

    @Test
    @DisplayName("Should create cache manager bean")
    void testCacheManagerBeanCreation() {
        assertThat(cacheManager).isNotNull();
    }

    @Test
    @DisplayName("Should have customers cache")
    void testCustomersCacheExists() {
        if (cacheManager != null) {
            assertThat(cacheManager.getCacheNames()).contains("customers");
        }
    }

    @Test
    @DisplayName("Should have inventory cache")
    void testInventoryCacheExists() {
        if (cacheManager != null) {
            assertThat(cacheManager.getCacheNames()).contains("inventory");
        }
    }

    @Test
    @DisplayName("Should have orders cache")
    void testOrdersCacheExists() {
        if (cacheManager != null) {
            assertThat(cacheManager.getCacheNames()).contains("orders");
        }
    }

    @Test
    @DisplayName("Should have payments cache")
    void testPaymentsCacheExists() {
        if (cacheManager != null) {
            assertThat(cacheManager.getCacheNames()).contains("payments");
        }
    }
}
