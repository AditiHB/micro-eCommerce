package com.ecommerce.common.health;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class CacheHealthIndicator implements HealthIndicator {

    private final RedisTemplate<String, Object> redisTemplate;

    @Override
    public Health health() {
        try {
            String healthKey = "health-check-" + System.currentTimeMillis();
            redisTemplate.opsForValue().set(healthKey, "ok", java.time.Duration.ofSeconds(10));

            Object value = redisTemplate.opsForValue().get(healthKey);
            redisTemplate.delete(healthKey);

            if ("ok".equals(value)) {
                log.debug("Cache (Redis) health check passed");
                return Health.up()
                    .withDetail("redis", "Connected")
                    .withDetail("cache", "Operational")
                    .build();
            }

            log.warn("Cache health check failed - value mismatch");
            return Health.down()
                .withDetail("redis", "Connected but unhealthy")
                .withDetail("cache", "Operational")
                .build();

        } catch (Exception ex) {
            log.error("Cache health check failed", ex);
            return Health.down()
                .withException(ex)
                .withDetail("redis", "Connection failed")
                .withDetail("error", ex.getMessage())
                .build();
        }
    }
}
