package com.ecommerce.paymentservice;

import com.ecommerce.common.config.JacksonConfig;
import com.ecommerce.common.config.KafkaEventConfig;
import com.ecommerce.common.config.RedisConfig;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * Wires up the shared {@code common} module's JPA-backed event sourcing repository/entity
 * and the safe subset of {@code common.config} (distributed caching, Jackson, Kafka topics/
 * listener factories) for this service's full application context.
 *
 * <p>Imports {@link RedisConfig}, not {@code CacheConfig} - confirmed live, under
 * k8s/overlays/postgres with 2 replicas, that {@code CacheConfig}'s per-instance
 * {@code ConcurrentMapCacheManager} makes one replica serve a stale cached read indefinitely
 * after another replica's Kafka listener updates and evicts only its own local cache: polling
 * the same order through the gateway alternated between COMPLETED and a stale PENDING
 * depending on which pod answered. Needs each service's Redis connection to actually point at
 * the real `redis` Service rather than its own stale {@code spring.redis.*} config (Spring
 * Boot 3's autoconfigured connection factory binds {@code spring.data.redis.*}) - see
 * SPRING_DATA_REDIS_HOST/PORT in k8s/base's Deployment for this service.
 *
 * <p>Deliberately kept as its own {@code @Configuration} bean (rather than annotating the
 * {@code @SpringBootApplication} class directly) so that {@code @WebMvcTest} slice tests -
 * which filter out non-web {@code @Configuration} beans but always process the main
 * application class verbatim - don't try to stand up JPA/EntityManagerFactory infrastructure
 * that a web-layer-only test slice never provides.
 */
@Configuration
@EnableJpaRepositories(basePackages = {"com.ecommerce.paymentservice", "com.ecommerce.common.eventsourcing", "com.ecommerce.common.repository"})
@EntityScan(basePackages = {"com.ecommerce.paymentservice", "com.ecommerce.common.eventsourcing", "com.ecommerce.common.entity"})
@Import({RedisConfig.class, JacksonConfig.class, KafkaEventConfig.class})
public class CommonIntegrationConfig {
}
