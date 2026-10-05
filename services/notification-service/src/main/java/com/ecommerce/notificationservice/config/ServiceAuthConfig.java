package com.ecommerce.notificationservice.config;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.ssl.SslBundles;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.converter.FormHttpMessageConverter;
import org.springframework.security.oauth2.client.AuthorizedClientServiceOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.InMemoryOAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientProvider;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientProviderBuilder;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.endpoint.DefaultClientCredentialsTokenResponseClient;
import org.springframework.security.oauth2.client.endpoint.OAuth2ClientCredentialsGrantRequest;
import org.springframework.security.oauth2.client.http.OAuth2ErrorResponseErrorHandler;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.endpoint.OAuth2AccessTokenResponse;
import org.springframework.security.oauth2.core.http.converter.OAuth2AccessTokenResponseHttpMessageConverter;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.Arrays;

/**
 * This service's own machine identity. It calls customer-service and order-service on behalf of
 * no particular user, so it authenticates as a Keycloak <em>service account</em> using the OAuth2
 * client-credentials grant: it holds a client id and secret (injected from the environment or
 * Vault, never committed), exchanges them for a short-lived access token carrying the SERVICE
 * role, and caches that token until shortly before it expires.
 *
 * <p>This replaces the previous scheme in which the service minted its own JWT with the platform's
 * shared signing secret and relied on a seeded row in each peer's users table.
 */
@Configuration
@Slf4j
public class ServiceAuthConfig {

    public static final String REGISTRATION_ID = "keycloak";

    @Bean
    @ConfigurationProperties(prefix = "ecommerce.service-auth")
    public ServiceAuthProperties serviceAuthProperties() {
        return new ServiceAuthProperties();
    }

    @Bean
    public ClientRegistrationRepository clientRegistrationRepository(ServiceAuthProperties props) {
        ClientRegistration registration = ClientRegistration.withRegistrationId(REGISTRATION_ID)
                .clientId(props.getClientId())
                .clientSecret(props.getClientSecret())
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
                .tokenUri(props.getTokenUri())
                .build();
        return new InMemoryClientRegistrationRepository(registration);
    }

    @Bean
    public OAuth2AuthorizedClientService authorizedClientService(ClientRegistrationRepository registrations) {
        return new InMemoryOAuth2AuthorizedClientService(registrations);
    }

    @Bean
    public OAuth2AuthorizedClientManager authorizedClientManager(
            ClientRegistrationRepository registrations,
            OAuth2AuthorizedClientService clients,
            ServiceAuthProperties props,
            ObjectProvider<SslBundles> sslBundles) {

        // The token endpoint is an internal HTTPS service in the secure profiles, so the token client
        // must be able to use a private trust store (the platform CA) instead of the JVM default.
        RestTemplateBuilder builder = new RestTemplateBuilder()
                .setConnectTimeout(Duration.ofSeconds(3))
                .setReadTimeout(Duration.ofSeconds(5));
        if (StringUtils.hasText(props.getSslBundle())) {
            builder = builder.setSslBundle(sslBundles.getObject().getBundle(props.getSslBundle()));
        }
        RestTemplate tokenClient = builder
                .messageConverters(new FormHttpMessageConverter(), new OAuth2AccessTokenResponseHttpMessageConverter())
                .errorHandler(new OAuth2ErrorResponseErrorHandler())
                .build();

        DefaultClientCredentialsTokenResponseClient responseClient = new DefaultClientCredentialsTokenResponseClient();
        responseClient.setRestOperations(tokenClient);

        OAuth2AuthorizedClientProvider provider = OAuth2AuthorizedClientProviderBuilder.builder()
                .clientCredentials(c -> c.accessTokenResponseClient(responseClient))
                .build();

        AuthorizedClientServiceOAuth2AuthorizedClientManager manager =
                new AuthorizedClientServiceOAuth2AuthorizedClientManager(registrations, clients);
        manager.setAuthorizedClientProvider(provider);
        return manager;
    }

    @Bean
    public ServiceAccountAuthInterceptor serviceAccountAuthInterceptor(OAuth2AuthorizedClientManager manager,
                                                                       ServiceAuthProperties props) {
        return new ServiceAccountAuthInterceptor(manager, props.getClientId());
    }

    @Validated
    @Getter
    @Setter
    public static class ServiceAuthProperties {
        /** Keycloak token endpoint, as reachable from this service. */
        @NotBlank
        private String tokenUri;
        @NotBlank
        private String clientId;
        /** Never defaulted: injected from the environment or Vault. A blank secret stops the service starting. */
        @NotBlank
        private String clientSecret;
        /** Optional Spring Boot SSL bundle used to reach the token endpoint over TLS. */
        private String sslBundle;
    }
}
