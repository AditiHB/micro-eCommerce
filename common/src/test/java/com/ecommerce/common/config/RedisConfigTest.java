package com.ecommerce.common.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.connection.RedisConnectionFactory;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.mock;

/**
 * RedisConfig only exposes a Redis-backed CacheManager bean (not a RedisTemplate),
 * and that bean method requires a RedisConnectionFactory - which Spring Boot normally
 * auto-configures against a real Redis instance. For this unit test we supply a mock
 * RedisConnectionFactory so the CacheManager bean can be created without a live Redis.
 */
@SpringBootTest(classes = {RedisConfig.class, RedisConfigTest.TestRedisConnectionConfig.class})
@DisplayName("RedisConfig Integration Tests")
class RedisConfigTest {

    @Autowired
    private CacheManager cacheManager;

    @Test
    @DisplayName("Should create Redis-backed CacheManager bean")
    void testCacheManagerBeanCreation() {
        assertThat(cacheManager).isNotNull();
    }

    @TestConfiguration
    static class TestRedisConnectionConfig {
        @Bean
        RedisConnectionFactory redisConnectionFactory() {
            return mock(RedisConnectionFactory.class);
        }
    }
}
