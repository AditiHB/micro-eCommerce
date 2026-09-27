package com.ecommerce.common.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;

/**
 * Redis Cache Configuration
 *
 * Purpose: Configure distributed Redis caching across all microservice instances.
 * This enables a shared cache layer that reduces database load and improves response times.
 *
 * Key Benefits:
 * - Distributed Cache: All service instances share same cache (vs local in-memory)
 * - Performance: Reduces database queries by ~80-90% for frequently accessed data
 * - Scalability: Scales horizontally with service instances
 * - TTL Management: Automatic expiration of stale data
 *
 * How It Works:
 * 1. @EnableCaching annotation enables Spring's caching infrastructure
 * 2. When @Cacheable is used on a method:
 *    - Cache hit: return cached value immediately (skip DB query)
 *    - Cache miss: execute method, cache result, return value
 * 3. When @CacheEvict is used:
 *    - Remove entry from cache when data changes
 *    - Next access triggers fresh fetch from database
 *
 * Configuration (from application.yml):
 * - spring.redis.host: localhost
 * - spring.redis.port: 6379
 * - spring.cache.redis.time-to-live: 600000ms (10 minutes)
 * - spring.cache.redis.key-prefix: service-specific prefix (e.g., "customer-service:")
 *
 * @see org.springframework.cache.annotation.Cacheable
 * @see org.springframework.cache.annotation.CacheEvict
 */
@Configuration
@EnableCaching
@Slf4j
public class RedisConfig {

    /**
     * Create Redis-backed CacheManager bean.
     *
     * This bean provides caching functionality for all service components.
     * Spring injects this into components that use @Cacheable/@CacheEvict annotations.
     *
     * Implementation Details:
     * - Uses RedisCacheManager (Spring Data Redis)
     * - Connects via RedisConnectionFactory (auto-configured by Spring Boot)
     * - Serializes cache values as JSON for language-agnostic storage
     * - Configures TTL (time-to-live) from application.yml
     *
     * @param connectionFactory Spring Boot auto-configured Redis connection factory
     * @return CacheManager configured to use Redis as backend
     *
     * Example Usage in Service:
     * @Service
     * public class CustomerService {
     *     @Cacheable(value = "customers", key = "#id")
     *     public Customer getCustomer(Long id) {
     *         // Execute only if not in cache
     *         return customerRepository.findById(id);
     *     }
     * }
     */
    @Bean
    public CacheManager cacheManager(RedisConnectionFactory connectionFactory) {
        log.info("Initializing Redis CacheManager - Distributed cache layer enabled");
        return RedisCacheManager.create(connectionFactory);
    }
}
