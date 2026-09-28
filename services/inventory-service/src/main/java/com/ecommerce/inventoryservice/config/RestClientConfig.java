package com.ecommerce.inventoryservice.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ssl.SslBundles;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

import javax.net.ssl.SSLContext;
import java.time.Duration;

@Configuration
@Slf4j
@RequiredArgsConstructor
public class RestClientConfig {

    private final SslBundles sslBundles;

    @Bean
    public RestTemplate restTemplate(RestTemplateBuilder builder) {
        log.info("Initializing RestTemplate with mTLS configuration");
        return builder
            .requestFactory(() -> {
                var factory = new org.springframework.http.client.HttpComponentsClientHttpRequestFactory();
                factory.setReadTimeout(Duration.ofSeconds(30));
                factory.setConnectTimeout(Duration.ofSeconds(10));
                return factory;
            })
            .setConnectTimeout(Duration.ofSeconds(10))
            .setReadTimeout(Duration.ofSeconds(30))
            .build();
    }

    @Bean(name = "orderServiceRestTemplate")
    public RestTemplate orderServiceRestTemplate(RestTemplateBuilder builder) {
        log.info("Initializing mTLS RestTemplate for Order Service");
        return builder
            .requestFactory(() -> createSslHttpRequestFactory("order-service"))
            .setConnectTimeout(Duration.ofSeconds(10))
            .setReadTimeout(Duration.ofSeconds(30))
            .build();
    }

    private org.springframework.http.client.HttpComponentsClientHttpRequestFactory
            createSslHttpRequestFactory(String serviceName) {
        try {
            var factory = new org.springframework.http.client.HttpComponentsClientHttpRequestFactory();

            var sslBundle = sslBundles.getBundle("mTLS");
            SSLContext sslContext = sslBundle.createSslContext();

            var httpClient = org.apache.http.impl.client.HttpClients.custom()
                .setSSLContext(sslContext)
                .setSSLHostnameVerifier(org.apache.http.conn.ssl.NoopHostnameVerifier.INSTANCE)
                .build();

            factory.setHttpClient(httpClient);
            factory.setConnectTimeout((int) Duration.ofSeconds(10).toMillis());
            factory.setReadTimeout((int) Duration.ofSeconds(30).toMillis());

            log.info("mTLS RestTemplate created for service: {}", serviceName);
            return factory;
        } catch (Exception e) {
            log.warn("Failed to create mTLS RestTemplate for {}, falling back to default", serviceName, e);
            var factory = new org.springframework.http.client.HttpComponentsClientHttpRequestFactory();
            factory.setConnectTimeout((int) Duration.ofSeconds(10).toMillis());
            factory.setReadTimeout((int) Duration.ofSeconds(30).toMillis());
            return factory;
        }
    }
}
