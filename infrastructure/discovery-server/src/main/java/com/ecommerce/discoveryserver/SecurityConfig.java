package com.ecommerce.discoveryserver;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;

/**
 * The service registry decides where traffic goes, so writing to it is a privileged operation.
 * Previously every request was permitted, which let anyone on the network register a rogue instance
 * under a real service name and receive the gateway's traffic (including bearer tokens). Now every
 * registry call, and the dashboard, needs HTTP Basic credentials from
 * {@code spring.security.user.*}; each client carries them in its {@code defaultZone} URL. Only the
 * container health probes stay open.
 *
 * <p>CSRF is disabled for {@code /eureka/**} because Eureka clients register through plain
 * POST/PUT/DELETE with no CSRF token; the endpoints are protected by Basic auth instead.
 */
@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.ignoringRequestMatchers(new AntPathRequestMatcher("/eureka/**")))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/actuator/health/**", "/actuator/health").permitAll()
                        .anyRequest().authenticated())
                .httpBasic(Customizer.withDefaults());

        return http.build();
    }
}
