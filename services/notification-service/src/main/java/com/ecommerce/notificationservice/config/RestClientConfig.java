package com.ecommerce.notificationservice.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ssl.SslBundles;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

/**
 * REST client configuration for Notification Service's two outbound calls:
 * Customer Service (to resolve recipient email) and Order Service (to
 * resolve the customer owning an order). Mirrors the mTLS-aware RestTemplate
 * pattern used by the other services.
 */
@Configuration
@Slf4j
@RequiredArgsConstructor
public class RestClientConfig {

    private final SslBundles sslBundles;

    @Bean(name = "customerServiceRestTemplate")
    public RestTemplate customerServiceRestTemplate(RestTemplateBuilder builder) {
        log.info("Initializing mTLS RestTemplate for Customer Service");
        return builder
            .setSslBundle(sslBundles.getBundle("mTLS"))
            .setConnectTimeout(Duration.ofSeconds(10))
            .setReadTimeout(Duration.ofSeconds(30))
            .build();
    }

    @Bean(name = "orderServiceRestTemplate")
    public RestTemplate orderServiceRestTemplate(RestTemplateBuilder builder) {
        log.info("Initializing mTLS RestTemplate for Order Service");
        return builder
            .setSslBundle(sslBundles.getBundle("mTLS"))
            .setConnectTimeout(Duration.ofSeconds(10))
            .setReadTimeout(Duration.ofSeconds(30))
            .build();
    }
}
