package com.ecommerce.notificationservice.config;

import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.security.oauth2.client.OAuth2AuthorizeRequest;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;

import java.io.IOException;

/**
 * Attaches this service's client-credentials access token to every outbound call. The manager
 * caches the token and fetches a fresh one shortly before it expires, so this adds no round trip
 * to the identity provider on the hot path.
 */
public class ServiceAccountAuthInterceptor implements ClientHttpRequestInterceptor {

    private final OAuth2AuthorizedClientManager manager;
    private final String principalName;

    public ServiceAccountAuthInterceptor(OAuth2AuthorizedClientManager manager, String principalName) {
        this.manager = manager;
        this.principalName = principalName;
    }

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution)
            throws IOException {
        OAuth2AuthorizeRequest authorizeRequest = OAuth2AuthorizeRequest
                .withClientRegistrationId(ServiceAuthConfig.REGISTRATION_ID)
                .principal(principalName)
                .build();
        OAuth2AuthorizedClient client = manager.authorize(authorizeRequest);
        if (client == null) {
            throw new IllegalStateException("Could not obtain a service-account access token");
        }
        request.getHeaders().setBearerAuth(client.getAccessToken().getTokenValue());
        return execution.execute(request, body);
    }
}
