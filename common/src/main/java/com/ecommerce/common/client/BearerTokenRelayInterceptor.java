package com.ecommerce.common.client;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.io.IOException;

/**
 * Forwards the caller's own access token on a service-to-service call made while serving that caller's request.
 * The downstream service then applies the same authorization the caller would get directly - a customer can
 * only ever validate their own customer record - instead of this service holding broader credentials.
 */
public class BearerTokenRelayInterceptor implements ClientHttpRequestInterceptor {

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution)
            throws IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof JwtAuthenticationToken jwt && !request.getHeaders().containsKey(HttpHeaders.AUTHORIZATION)) {
            request.getHeaders().setBearerAuth(jwt.getToken().getTokenValue());
        }
        return execution.execute(request, body);
    }
}
