package com.ecommerce.common.config;

import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;

import java.time.Duration;

/**
 * Redis Cache Configuration
 *
 * Imported by customer/order/inventory/payment-service's CommonIntegrationConfig
 * (not {@link CacheConfig} - see its javadoc). Fixed after being confirmed live,
 * under a Kubernetes deployment with 2 replicas per service and a real shared
 * Postgres database (k8s/overlays/postgres): with {@code CacheConfig}'s local
 * {@code ConcurrentMapCacheManager}, one order-service replica's Kafka listener
 * would update an order to COMPLETED and evict only its own local cache entry,
 * while the other replica - never having processed that Kafka message, and
 * with no shared cache to invalidate it - kept serving its own stale cached
 * PENDING read indefinitely. Polling the same order through the gateway (which
 * round-robins between pods) alternated between the two answers forever.
 * Switching to this class's distributed RedisCacheManager, plus pointing each
 * service's Redis connection at the real `redis` Kubernetes Service (see
 * SPRING_DATA_REDIS_HOST/PORT in k8s/base's Deployment for this service -
 * needed because each service's own application.yml configures the stale,
 * pre-Spring-Boot-3 {@code spring.redis.*} path rather than the
 * autoconfigured {@code spring.data.redis.*} one), resolved it: both replicas
 * now read the same cache entry.
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
 * Configuration actually read (everything else in each service's
 * application.yml {@code spring.redis.*}/{@code spring.cache.redis.key-prefix}
 * block is dead - see this class's connectivity note above and {@link
 * #cacheManager}'s own note on key-prefix not being wired up):
 * - spring.data.redis.host / spring.data.redis.port (via SPRING_DATA_REDIS_HOST/PORT)
 * - spring.cache.redis.time-to-live: 600000ms (10 minutes) default
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
    @Value("${spring.cache.redis.time-to-live:600000}")
    private long timeToLiveMs;

    @Bean
    public CacheManager cacheManager(RedisConnectionFactory connectionFactory) {
        log.info("Initializing Redis CacheManager - Distributed cache layer enabled, TTL={}ms", timeToLiveMs);
        // RedisCacheManager.create(connectionFactory) alone - the previous
        // implementation here - uses Spring Data Redis's default value
        // serializer (JdkSerializationRedisSerializer), not JSON as this
        // class's own javadoc claims, and ignores spring.cache.redis.time-to-live
        // entirely (that property only applies via Spring Boot's OWN
        // autoconfigured CacheManager, which backs off the moment any
        // user-defined CacheManager bean - this one - exists). Confirmed
        // live: the first real @Cacheable hit (GET /api/orders/{id}) threw
        // SerializationException -> IllegalArgumentException("DefaultSerializer
        // requires a Serializable payload but received an object of type
        // [...OrderResponse]") - none of this codebase's response DTOs
        // implement Serializable, nor should they need to just to be cached.
        // GenericJackson2JsonRedisSerializer actually serializes as JSON,
        // matching every other DTO's HTTP (de)serialization in this
        // codebase and requiring no changes to those DTOs; the explicit
        // entryTtl below makes good on this class's own TTL claim too.
        // spring.cache.redis.key-prefix is NOT wired up here (would need
        // .computePrefixWith(...) per cache) - left out since every
        // service's cache names (ORDERS_CACHE="orders", CUSTOMERS_CACHE=
        // "customers", etc.) are already distinct strings, so cross-service
        // key collisions in the shared Redis instance aren't a real risk.
        //
        // GenericJackson2JsonRedisSerializer's no-arg constructor builds its
        // own ObjectMapper with default typing already activated (embeds an
        // "@class" property so a value written by one pod deserializes back
        // into the right DTO on another), but that mapper has no
        // JavaTimeModule registered - confirmed live, the request right
        // after fixing the Serializable issue above threw
        // InvalidDefinitionException: "Java 8 date/time type
        // `java.time.LocalDateTime` not supported by default".
        //
        // The fix is NOT to pass in this service's own JacksonConfig
        // ObjectMapper bean via the ObjectMapper-accepting constructor -
        // tried that first, and it breaks default typing instead (that
        // constructor assumes the given mapper is already configured for
        // it): confirmed live, order-service's two pods then disagreed in a
        // new way - pod A (which had just written the cache entry) read its
        // own order back fine, pod B (reading the same Redis key cold)
        // threw "ClassCastException: class java.util.LinkedHashMap cannot
        // be cast to class ...OrderResponse" - the entry had been written
        // without type info, so Jackson deserialized it as a plain untyped
        // Map instead of the real DTO.
        //
        // configure() is built for exactly this: it hands you the
        // serializer's own already-correctly-typed internal mapper so you
        // can layer additional modules onto it without disturbing typing.
        GenericJackson2JsonRedisSerializer valueSerializer = new GenericJackson2JsonRedisSerializer();
        valueSerializer.configure(mapper -> mapper
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS));
        RedisCacheConfiguration cacheConfig = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofMillis(timeToLiveMs))
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(valueSerializer));
        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(cacheConfig)
                .build();
    }
}
