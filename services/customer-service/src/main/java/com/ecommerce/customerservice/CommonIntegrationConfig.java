package com.ecommerce.customerservice;

import com.ecommerce.common.config.JacksonConfig;
import com.ecommerce.common.config.RedisConfig;
import com.ecommerce.common.messaging.MessagingConfig;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * Wires the shared {@code common} module into this service: the JPA entities and repositories it owns
 * (event store, transactional outbox, idempotent-consumer ledger, dead letters), Jackson, and the messaging
 * infrastructure (Kafka retry and dead-lettering, outbox relay) plus the one Redis-backed cache - this
 * service caches reference data.
 *
 * <p>Deliberately its own {@code @Configuration} (not on the {@code @SpringBootApplication} class) so that
 * {@code @WebMvcTest} slices - which skip non-web configuration but always process the main class - do not try
 * to stand up JPA infrastructure that a web-layer test never provides.
 */
@Configuration
@EnableJpaRepositories(basePackages = {"com.ecommerce.customerservice", "com.ecommerce.common.eventsourcing", "com.ecommerce.common.outbox", "com.ecommerce.common.inbox", "com.ecommerce.common.dlq"})
@EntityScan(basePackages = {"com.ecommerce.customerservice", "com.ecommerce.common.eventsourcing", "com.ecommerce.common.outbox", "com.ecommerce.common.inbox", "com.ecommerce.common.dlq"})
@Import({RedisConfig.class, JacksonConfig.class, MessagingConfig.class})
public class CommonIntegrationConfig {
}
