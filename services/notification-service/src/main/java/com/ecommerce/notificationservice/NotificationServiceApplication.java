package com.ecommerce.notificationservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityFilterAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.actuate.autoconfigure.security.servlet.ManagementWebSecurityAutoConfiguration;

/**
 * scanBasePackages/exclude mirror every other service's main class (see e.g.
 * InventoryServiceApplication): without them, this service never picked up
 * common.security's SecurityConfig/JwtAuthenticationFilter at all (its
 * @SpringBootApplication base package is com.ecommerce.notificationservice
 * only), so Spring Boot silently fell back to its own default security
 * auto-configuration - a random generated password over HTTP Basic,
 * completely unrelated to the JWTs the rest of the app issues. Every
 * endpoint here was effectively unreachable with a real login token.
 */
@SpringBootApplication(
    scanBasePackages = {
        "com.ecommerce.notificationservice",
        "com.ecommerce.common.security",
        "com.ecommerce.common.exception"
    },
    exclude = {SecurityAutoConfiguration.class, SecurityFilterAutoConfiguration.class, UserDetailsServiceAutoConfiguration.class, ManagementWebSecurityAutoConfiguration.class}
)
@EnableDiscoveryClient
public class NotificationServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(NotificationServiceApplication.class, args);
    }
}
