package com.ecommerce.discoveryserver;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * spring-cloud-starter-netflix-eureka-server pulls in Spring Security transitively,
 * which by default requires authentication for every request - including the
 * /eureka/** endpoints Eureka clients use to register themselves. Without this,
 * every service fails to register with "Cannot execute request on any known server".
 * CSRF is disabled because Eureka clients register via plain POST/PUT/DELETE with
 * no CSRF token.
 */
@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(authorize -> authorize.anyRequest().permitAll());

        return http.build();
    }
}
