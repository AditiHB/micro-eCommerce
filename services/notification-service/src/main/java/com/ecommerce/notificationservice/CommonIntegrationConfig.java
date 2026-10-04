package com.ecommerce.notificationservice;

import com.ecommerce.common.config.KafkaEventConfig;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * Wires up the shared {@code common} module's JPA-backed {@code User}
 * entity/repository (common.security's JwtAuthenticationFilter needs it to
 * validate a request's JWT against this service's own local copy of the
 * user), and {@code KafkaEventConfig} - without it, this service fell back
 * to Spring Boot's plain auto-configured Kafka consumer, which expects a
 * {@code __TypeId__} header to resolve the event type. The producers
 * (order-service/payment-service) import this same config and explicitly
 * disable that header (@{code JsonSerializer.ADD_TYPE_INFO_HEADERS=false}),
 * relying on the consumer's {@code JsonDeserializer.VALUE_DEFAULT_TYPE}
 * instead - so every Kafka consumer in this app needs this config too, or
 * it fails with "No type information in headers and no default type
 * provided" and silently never processes a single event.
 *
 * <p>Deliberately kept as its own {@code @Configuration} bean (rather than
 * annotating the {@code @SpringBootApplication} class directly) so that
 * {@code @WebMvcTest} slice tests - which filter out non-web
 * {@code @Configuration} beans but always process the main application
 * class verbatim - don't try to stand up JPA/EntityManagerFactory/Kafka
 * infrastructure that a web-layer-only test slice never provides. See the
 * equivalent class in customer-service/inventory-service/etc.
 */
@Configuration
@EnableJpaRepositories(basePackages = {"com.ecommerce.notificationservice", "com.ecommerce.common.repository"})
@EntityScan(basePackages = {"com.ecommerce.notificationservice", "com.ecommerce.common.entity"})
@Import(KafkaEventConfig.class)
public class CommonIntegrationConfig {
}
