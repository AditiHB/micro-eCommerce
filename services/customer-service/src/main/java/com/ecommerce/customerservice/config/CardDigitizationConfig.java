package com.ecommerce.customerservice.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class CardDigitizationConfig {

    @Bean
    @ConfigurationProperties(prefix = "ecommerce.card-digitization")
    public CardDigitizationProperties cardDigitizationProperties() {
        return new CardDigitizationProperties();
    }
}
