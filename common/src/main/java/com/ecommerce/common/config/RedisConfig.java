package com.ecommerce.common.config;

import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.cache.interceptor.LoggingCacheErrorHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.cache.RedisCacheWriter;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;

import java.time.Duration;

/**
 * The one cache: Redis, shared by every replica of a service (a per-instance cache served stale reads the
 * moment a second replica ran - confirmed live on Kubernetes). Imported only by services that actually cache
 * reference data (customer, product); see {@link CacheConfig} for what is, and is not, cached.
 *
 * <p><b>A cache outage must not take writes down with it.</b> Every cache failure - Redis unreachable, a value
 * that cannot be (de)serialized - is logged and treated as a miss, so the service falls through to the
 * database instead of failing the request.
 *
 * <p>Values are stored as JSON with Jackson default typing (an {@code @class} property, so any replica can read
 * what another wrote) and a {@link JavaTimeModule} for the DTOs' date fields. The TTL comes from
 * {@code spring.cache.redis.time-to-live} (default 10 minutes) and is a safety net only: entries are evicted
 * by key when their record changes.
 */
@Configuration
@EnableCaching
@Slf4j
public class RedisConfig implements CachingConfigurer {

    @Value("${spring.cache.redis.time-to-live:600000}")
    private long timeToLiveMs;

    @Bean
    @Override
    public CacheErrorHandler errorHandler() {
        return new LoggingCacheErrorHandler(false);
    }

    @Bean
    public CacheManager cacheManager(RedisConnectionFactory connectionFactory) {
        log.info("Initializing Redis CacheManager (TTL {}ms)", timeToLiveMs);
        // GenericJackson2JsonRedisSerializer builds its own ObjectMapper with default typing already switched
        // on. Handing it an application mapper instead breaks that typing (entries come back as untyped maps),
        // so layer the date/time module onto the serializer's own mapper through configure().
        GenericJackson2JsonRedisSerializer valueSerializer = new GenericJackson2JsonRedisSerializer();
        valueSerializer.configure(mapper -> mapper
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS));
        RedisCacheConfiguration cacheConfig = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofMillis(timeToLiveMs))
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(valueSerializer));
        RedisCacheManager manager = new RedisCacheManager(RedisCacheWriter.nonLockingRedisCacheWriter(connectionFactory), cacheConfig) {
            // Every cache is wrapped so that no cache failure - on any path, including the eviction that runs
            // after a transaction commits - can fail a request.
            @Override
            protected Cache decorateCache(Cache cache) {
                return super.decorateCache(new FailSafeCache(cache));
            }
        };
        // Puts and evictions happen after the surrounding transaction COMMITS, so a reader can never
        // re-populate the cache with the old value between an eviction and the commit of the change.
        manager.setTransactionAware(true);
        return manager;
    }
}
