package com.ecommerce.common.messaging;

import com.ecommerce.common.config.KafkaEventConfig;
import com.ecommerce.common.dlq.DeadLetterConfig;
import com.ecommerce.common.inbox.InboxService;
import com.ecommerce.common.outbox.OutboxProperties;
import com.ecommerce.common.outbox.OutboxRelay;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Switches on the messaging infrastructure for a service: Kafka wiring (retry, dead-lettering, topics), the
 * transactional outbox and its relay, the idempotent-consumer ledger and the dead-letter tooling. Imported by
 * each service's {@code CommonIntegrationConfig}, together with the JPA packages that hold these entities:
 * {@code common.eventsourcing}, {@code common.outbox}, {@code common.inbox} and {@code common.dlq}.
 */
@Configuration
@EnableScheduling
@EnableConfigurationProperties(OutboxProperties.class)
@ComponentScan(basePackageClasses = {OutboxRelay.class, InboxService.class})
@Import({KafkaEventConfig.class, DeadLetterConfig.class})
public class MessagingConfig {
}
