package com.ecommerce.common.testsupport;

import com.ecommerce.common.config.JacksonConfig;
import com.ecommerce.common.eventsourcing.EventSourcingService;
import com.ecommerce.common.events.EventPublisher;
import com.ecommerce.common.messaging.MessagingConfig;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.autoconfigure.security.oauth2.resource.servlet.OAuth2ResourceServerAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityFilterAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.actuate.autoconfigure.data.redis.RedisHealthContributorAutoConfiguration;
import org.springframework.boot.actuate.autoconfigure.security.servlet.ManagementWebSecurityAutoConfiguration;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/** The smallest application that has the shared messaging code in it, for the common module's integration tests. */
@Configuration
@EnableAutoConfiguration(exclude = {
        SecurityAutoConfiguration.class, SecurityFilterAutoConfiguration.class,
        UserDetailsServiceAutoConfiguration.class, ManagementWebSecurityAutoConfiguration.class,
        OAuth2ResourceServerAutoConfiguration.class,
        RedisAutoConfiguration.class, RedisRepositoriesAutoConfiguration.class, RedisHealthContributorAutoConfiguration.class})
@EntityScan(basePackages = {"com.ecommerce.common.eventsourcing", "com.ecommerce.common.outbox",
        "com.ecommerce.common.inbox", "com.ecommerce.common.dlq"})
@EnableJpaRepositories(basePackages = {"com.ecommerce.common.eventsourcing", "com.ecommerce.common.outbox",
        "com.ecommerce.common.inbox", "com.ecommerce.common.dlq"})
@Import({MessagingConfig.class, JacksonConfig.class, EventPublisher.class, EventSourcingService.class})
public class TestMessagingApplication {
}
