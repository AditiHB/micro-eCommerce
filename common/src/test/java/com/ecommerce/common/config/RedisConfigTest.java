package com.ecommerce.common.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@DisplayName("RedisConfig Integration Tests")
class RedisConfigTest {

    @Autowired(required = false)
    private RedisTemplate<String, Object> redisTemplate;

    @Test
    @DisplayName("Should create RedisTemplate bean")
    void testRedisTemplateBeanCreation() {
        assertThat(redisTemplate).isNotNull();
    }

    @Test
    @DisplayName("Should configure Redis connection factory")
    void testRedisConnectionFactory() {
        if (redisTemplate != null) {
            assertThat(redisTemplate.getConnectionFactory()).isNotNull();
        }
    }
}
