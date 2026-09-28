package com.ecommerce.orderservice;

import com.ecommerce.common.config.CacheConfig;
import com.ecommerce.common.config.JacksonConfig;
import com.ecommerce.common.config.KafkaEventConfig;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * Wires up the shared {@code common} module's JPA-backed event sourcing repository/entity
 * and the safe subset of {@code common.config} (caching, Jackson, Kafka topics/listener
 * factories) for this service's full application context.
 *
 * <p>Deliberately kept as its own {@code @Configuration} bean (rather than annotating the
 * {@code @SpringBootApplication} class directly) so that {@code @WebMvcTest} slice tests -
 * which filter out non-web {@code @Configuration} beans but always process the main
 * application class verbatim - don't try to stand up JPA/EntityManagerFactory infrastructure
 * that a web-layer-only test slice never provides.
 */
@Configuration
@EnableJpaRepositories(basePackages = {"com.ecommerce.orderservice", "com.ecommerce.common.eventsourcing"})
@EntityScan(basePackages = {"com.ecommerce.orderservice", "com.ecommerce.common.eventsourcing"})
@Import({CacheConfig.class, JacksonConfig.class, KafkaEventConfig.class})
public class CommonIntegrationConfig {
}
