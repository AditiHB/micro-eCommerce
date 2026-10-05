package com.ecommerce.notificationservice.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Notification-service's own configuration: switches on {@link NotificationProperties}. (The one outbound HTTP
 * client - to the customer service - is built where it is used, in {@code CustomerClient}, with short explicit
 * timeouts; the former shared RestTemplates with 30-second read timeouts are gone.)
 */
@Configuration
@EnableConfigurationProperties(NotificationProperties.class)
public class RestClientConfig {
}
