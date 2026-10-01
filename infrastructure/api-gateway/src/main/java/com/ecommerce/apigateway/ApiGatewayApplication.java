package com.ecommerce.apigateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.reactive.ReactiveSecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.security.reactive.ReactiveUserDetailsServiceAutoConfiguration;
import org.springframework.boot.actuate.autoconfigure.security.reactive.ReactiveManagementWebSecurityAutoConfiguration;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

// Spring Security is only on the classpath as a transitive dependency (via spring-cloud-starter-gateway's
// own use of spring-security-crypto/rsa); this service authenticates/authorizes requests itself via its
// own AuthenticationFilter and RateLimitingFilter GlobalFilters, not Spring Security. Without these
// exclusions, Spring Boot's reactive security auto-configuration silently activates its maximally
// restrictive default (HTTP Basic auth behind a randomly generated password logged at startup),
// rejecting every request the gateway is supposed to route - confirmed by booting this service locally.
@SpringBootApplication(exclude = {
    ReactiveSecurityAutoConfiguration.class,
    ReactiveUserDetailsServiceAutoConfiguration.class,
    ReactiveManagementWebSecurityAutoConfiguration.class
})
@EnableDiscoveryClient
public class ApiGatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(ApiGatewayApplication.class, args);
    }
}
