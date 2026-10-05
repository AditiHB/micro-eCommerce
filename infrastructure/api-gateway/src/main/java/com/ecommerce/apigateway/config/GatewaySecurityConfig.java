package com.ecommerce.apigateway.config;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.autoconfigure.web.reactive.function.client.WebClientSsl;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.header.ReferrerPolicyServerHttpHeadersWriter;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * The edge authenticates every API call: a request without a valid access token from the identity
 * provider (RS256 signature, expiry, issuer and audience all checked) never reaches a route, a
 * rate limiter or a downstream service. This replaces the hand-written {@code AuthenticationFilter},
 * which only ran on explicitly configured routes, parsed the token twice, and checked it against a
 * secret shared with every other service.
 *
 * <p>Fine-grained authorization (which role may call which endpoint, which records a customer may
 * see) stays in each service: the gateway forwards the original bearer token untouched, and every
 * service validates it again, so a compromised or bypassed gateway grants nothing.
 */
@Configuration
@EnableWebFluxSecurity
@EnableConfigurationProperties(GatewaySecurityProperties.class)
public class GatewaySecurityConfig {

    @Bean
    public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http) {
        http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
                .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
                .logout(ServerHttpSecurity.LogoutSpec::disable)
                .headers(headers -> headers
                        .referrerPolicy(ref -> ref.policy(ReferrerPolicyServerHttpHeadersWriter.ReferrerPolicy.NO_REFERRER))
                        .hsts(hsts -> hsts.includeSubdomains(true).maxAge(java.time.Duration.ofDays(365))))
                .authorizeExchange(exchanges -> exchanges
                        // Container/Kubernetes probes; metrics live on the separate management port.
                        .pathMatchers("/actuator/health/**", "/actuator/health", "/actuator/info").permitAll()
                        // CORS preflight carries no credentials by definition.
                        .pathMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        // Circuit-breaker fallback forwards.
                        .pathMatchers("/fallback/**").permitAll()
                        .pathMatchers("/api/**").authenticated()
                        .anyExchange().denyAll())
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()));
        return http.build();
    }

    @Bean
    @ConditionalOnMissingBean(ReactiveJwtDecoder.class)
    public ReactiveJwtDecoder reactiveJwtDecoder(GatewaySecurityProperties props,
                                                 ObjectProvider<WebClientSsl> webClientSsl) {
        WebClient.Builder client = WebClient.builder();
        if (StringUtils.hasText(props.getJwksSslBundle())) {
            client = client.apply(webClientSsl.getObject().fromBundle(props.getJwksSslBundle()));
        }

        NimbusReactiveJwtDecoder decoder = NimbusReactiveJwtDecoder.withJwkSetUri(props.getJwkSetUri())
                .jwsAlgorithm(SignatureAlgorithm.RS256)
                .webClient(client.build())
                .build();
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(props.getIssuer()),
                audience(props.getAudience())));
        return decoder;
    }

    private static OAuth2TokenValidator<Jwt> audience(String audience) {
        OAuth2Error error = new OAuth2Error("invalid_token", "The required audience is missing", null);
        return jwt -> jwt.getAudience() != null && jwt.getAudience().contains(audience)
                ? OAuth2TokenValidatorResult.success()
                : OAuth2TokenValidatorResult.failure(error);
    }
}
