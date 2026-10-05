package com.ecommerce.common.security;

import com.ecommerce.common.constants.ApiConstants;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.ssl.SslBundles;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.util.StringUtils;

import java.time.Duration;

/**
 * Every service is an OAuth2 resource server: it holds no passwords, mints no tokens and shares no
 * secret with its peers. It trusts RS256 access tokens issued by the identity provider (Keycloak),
 * verified against the provider's published keys (JWKS), and checks issuer, expiry and audience.
 *
 * <p>Authorization is deny-by-default. The rules below say which <em>kinds</em> of caller may use
 * each endpoint; {@link CurrentUser} then enforces which <em>records</em> a customer may touch.
 * The matrix is shared because every service scans this class but only serves its own paths.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@EnableConfigurationProperties(SecurityProperties.class)
@RequiredArgsConstructor
public class SecurityConfig {

    private static final String API = ApiConstants.API_PREFIX;
    private static final String CUSTOMERS = API + "/customers/**";
    private static final String ORDERS = API + "/orders/**";
    private static final String PAYMENTS = API + "/payments/**";
    private static final String INVENTORY = API + "/inventory/**";
    private static final String PRODUCTS = API + "/products/**";
    private static final String NOTIFICATIONS = API + "/notifications/**";
    private static final String DEAD_LETTERS = API + "/dead-letters/**";

    private final SecurityProperties properties;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                // Stateless bearer-token API: no cookies, no session, so there is nothing for CSRF to ride on.
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .headers(headers -> {
                    headers.frameOptions(HeadersConfigurer.FrameOptionsConfig::deny);
                    headers.referrerPolicy(ref -> ref.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.NO_REFERRER));
                    headers.httpStrictTransportSecurity(hsts -> hsts.includeSubDomains(true).maxAgeInSeconds(31_536_000));
                    if (!properties.isDocsPublic()) {
                        headers.contentSecurityPolicy(csp ->
                                csp.policyDirectives("default-src 'none'; frame-ancestors 'none'"));
                    }
                })
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(new KeycloakJwtAuthenticationConverter())))
                .authorizeHttpRequests(authorize -> {
                    // Liveness/readiness probes only. Metrics and the rest of /actuator live on the
                    // separate management port, which is not routed by the gateway.
                    authorize.requestMatchers("/actuator/health/**", "/actuator/health", "/actuator/info").permitAll();

                    if (properties.isDocsPublic()) {
                        authorize.requestMatchers("/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**").permitAll();
                    }

                    // --- customer-service ---
                    authorize.requestMatchers(HttpMethod.GET, API + "/customers").hasAnyRole(Roles.ADMIN, Roles.MANAGER);
                    authorize.requestMatchers(HttpMethod.GET, CUSTOMERS)
                            .hasAnyRole(Roles.USER, Roles.ADMIN, Roles.MANAGER, Roles.SERVICE);
                    authorize.requestMatchers(HttpMethod.POST, CUSTOMERS).hasAnyRole(Roles.ADMIN, Roles.MANAGER);
                    authorize.requestMatchers(HttpMethod.PUT, CUSTOMERS).hasAnyRole(Roles.ADMIN, Roles.MANAGER);
                    authorize.requestMatchers(HttpMethod.DELETE, CUSTOMERS).hasRole(Roles.ADMIN);

                    // --- order-service (USER is limited to own orders by CurrentUser) ---
                    authorize.requestMatchers(HttpMethod.GET, ORDERS)
                            .hasAnyRole(Roles.USER, Roles.ADMIN, Roles.MANAGER, Roles.SERVICE);
                    authorize.requestMatchers(HttpMethod.POST, ORDERS)
                            .hasAnyRole(Roles.USER, Roles.ADMIN, Roles.MANAGER);
                    authorize.requestMatchers(HttpMethod.PUT, ORDERS).hasAnyRole(Roles.ADMIN, Roles.MANAGER);

                    // --- payment-service: charges are made only by the saga; back office can read and refund ---
                    authorize.requestMatchers(HttpMethod.GET, PAYMENTS).hasAnyRole(Roles.ADMIN, Roles.MANAGER);
                    authorize.requestMatchers(HttpMethod.POST, PAYMENTS).hasAnyRole(Roles.ADMIN, Roles.MANAGER);

                    // --- inventory-service ---
                    authorize.requestMatchers(HttpMethod.GET, INVENTORY).hasAnyRole(Roles.ADMIN, Roles.MANAGER);
                    authorize.requestMatchers(HttpMethod.PUT, INVENTORY).hasAnyRole(Roles.ADMIN, Roles.MANAGER);
                    authorize.requestMatchers(HttpMethod.POST, INVENTORY).hasRole(Roles.ADMIN);

                    // --- product-service ---
                    authorize.requestMatchers(HttpMethod.GET, PRODUCTS)
                            .hasAnyRole(Roles.USER, Roles.ADMIN, Roles.MANAGER, Roles.SERVICE);
                    authorize.requestMatchers(HttpMethod.POST, PRODUCTS).hasAnyRole(Roles.ADMIN, Roles.MANAGER);
                    authorize.requestMatchers(HttpMethod.PUT, PRODUCTS).hasAnyRole(Roles.ADMIN, Roles.MANAGER);
                    authorize.requestMatchers(HttpMethod.DELETE, PRODUCTS).hasAnyRole(Roles.ADMIN, Roles.MANAGER);

                    // --- notification-service ---
                    authorize.requestMatchers(HttpMethod.GET, NOTIFICATIONS).hasAnyRole(Roles.ADMIN, Roles.MANAGER);

                    // --- operator tooling: every service exposes the dead letters of its own consumers ---
                    authorize.requestMatchers(HttpMethod.GET, DEAD_LETTERS).hasRole(Roles.ADMIN);
                    authorize.requestMatchers(HttpMethod.POST, DEAD_LETTERS).hasRole(Roles.ADMIN);

                    // Anything not listed above is not an endpoint anyone may call.
                    authorize.anyRequest().denyAll();
                });

        return http.build();
    }

    /**
     * Verifies RS256 signatures against the identity provider's JWKS, then checks expiry, issuer
     * and audience. Created lazily on first use of the key set, so a service can start while the
     * identity provider is still booting.
     */
    @Bean
    @ConditionalOnMissingBean(JwtDecoder.class)
    public JwtDecoder jwtDecoder(ObjectProvider<SslBundles> sslBundles) {
        RestTemplateBuilder client = new RestTemplateBuilder()
                .setConnectTimeout(Duration.ofSeconds(3))
                .setReadTimeout(Duration.ofSeconds(5));
        if (StringUtils.hasText(properties.getJwksSslBundle())) {
            client = client.setSslBundle(sslBundles.getObject().getBundle(properties.getJwksSslBundle()));
        }

        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(properties.getJwkSetUri())
                .jwsAlgorithm(SignatureAlgorithm.RS256)
                .restOperations(client.build())
                .build();
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(properties.getIssuer()),
                new AudienceValidator(properties.getAudience())));
        return decoder;
    }
}
