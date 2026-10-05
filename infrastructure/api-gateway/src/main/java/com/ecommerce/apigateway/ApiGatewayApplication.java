package com.ecommerce.apigateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.reactive.ReactiveSecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.security.reactive.ReactiveUserDetailsServiceAutoConfiguration;
import org.springframework.boot.actuate.autoconfigure.security.reactive.ReactiveManagementWebSecurityAutoConfiguration;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

// Security is configured explicitly in GatewaySecurityConfig (OAuth2 resource server, JWKS-validated
// RS256 tokens). The reactive auto-configurations below are excluded so Spring Boot never falls back
// to its default HTTP Basic user with a randomly generated password logged at startup.
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
