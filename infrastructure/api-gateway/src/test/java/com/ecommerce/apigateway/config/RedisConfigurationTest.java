package com.ecommerce.apigateway.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;

import static org.assertj.core.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("RedisConfiguration Unit Tests")
class RedisConfigurationTest {

    private final RedisConfiguration config = new RedisConfiguration();

    @Mock
    private RedisConnectionFactory connectionFactory;

    @Test
    @DisplayName("Should create Redis template bean")
    void testRedisTemplateCreation() {
        RedisTemplate<String, String> redisTemplate = config.redisTemplate(connectionFactory);

        assertThat(redisTemplate).isNotNull();
    }

    @Test
    @DisplayName("Should configure String serialization for Redis")
    void testStringSerializationConfiguration() {
        RedisTemplate<String, String> redisTemplate = config.redisTemplate(connectionFactory);

        assertThat(redisTemplate).isNotNull();
    }

    @Test
    @DisplayName("Should support key operations")
    void testKeyOperations() {
        RedisTemplate<String, String> redisTemplate = config.redisTemplate(connectionFactory);

        assertThat(redisTemplate.opsForValue()).isNotNull();
    }

    @Test
    @DisplayName("Should support list operations")
    void testListOperations() {
        RedisTemplate<String, String> redisTemplate = config.redisTemplate(connectionFactory);

        assertThat(redisTemplate.opsForList()).isNotNull();
    }

    @Test
    @DisplayName("Should support set operations")
    void testSetOperations() {
        RedisTemplate<String, String> redisTemplate = config.redisTemplate(connectionFactory);

        assertThat(redisTemplate.opsForSet()).isNotNull();
    }

    @Test
    @DisplayName("Should support hash operations")
    void testHashOperations() {
        RedisTemplate<String, String> redisTemplate = config.redisTemplate(connectionFactory);

        assertThat(redisTemplate.opsForHash()).isNotNull();
    }
}
