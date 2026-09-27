package com.ecommerce.apigateway.config;

import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class GatewayConfiguration {

    @Bean
    public RouteLocator customRouteLocator(RouteLocatorBuilder builder) {
        return builder.routes()
            .route("order-service", r -> r
                .path("/api/orders/**")
                .uri("lb://order-service"))

            .route("customer-service", r -> r
                .path("/api/customers/**")
                .uri("lb://customer-service"))

            .route("inventory-service", r -> r
                .path("/api/inventory/**")
                .uri("lb://inventory-service"))

            .route("payment-service", r -> r
                .path("/api/payments/**")
                .uri("lb://payment-service"))

            .build();
    }
}
