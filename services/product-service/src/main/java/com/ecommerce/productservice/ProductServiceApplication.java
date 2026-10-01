package com.ecommerce.productservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityFilterAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.actuate.autoconfigure.security.servlet.ManagementWebSecurityAutoConfiguration;
import org.springframework.kafka.annotation.EnableKafka;

@SpringBootApplication(
    scanBasePackages = {
        "com.ecommerce.productservice", 
        "com.ecommerce.common.events", 
        "com.ecommerce.common.eventsourcing", 
        "com.ecommerce.common.metrics", 
        "com.ecommerce.common.logging",
        "com.ecommerce.common.service",
        "com.ecommerce.common.security",
        "com.ecommerce.common.exception"
    },
    exclude = {SecurityAutoConfiguration.class, SecurityFilterAutoConfiguration.class, UserDetailsServiceAutoConfiguration.class, ManagementWebSecurityAutoConfiguration.class}
)
@EnableDiscoveryClient
@EnableKafka
public class ProductServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(ProductServiceApplication.class, args);
    }
}
