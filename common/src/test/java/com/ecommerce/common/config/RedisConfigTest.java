package com.ecommerce.common.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * RedisConfig needs a RedisConnectionFactory, which Spring Boot normally auto-configures against a real Redis. A
 * mock factory is enough to check the manager is wired the way production needs it.
 */
@SpringBootTest(classes = {RedisConfig.class, RedisConfigTest.TestRedisConnectionConfig.class})
@TestPropertySource(properties = "spring.cache.redis.time-to-live=120000")
@DisplayName("RedisConfig")
class RedisConfigTest {

    @Autowired
    private CacheManager cacheManager;

    @Autowired
    private CachingConfigurer configurer;

    @Test
    @DisplayName("the one cache manager is the shared, Redis-backed one")
    void cacheManagerIsRedis() {
        assertThat(cacheManager).isInstanceOf(RedisCacheManager.class);
    }

    @Test
    @DisplayName("applies a cache error handler so a Redis outage cannot break writes")
    void errorHandlerIsApplied() {
        assertThat(configurer.errorHandler()).isNotNull();
    }

    @TestConfiguration
    static class TestRedisConnectionConfig {
        @Bean
        RedisConnectionFactory redisConnectionFactory() {
            return mock(RedisConnectionFactory.class);
        }
    }
}
