package com.ecommerce.common.client;

import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;

/**
 * The one way a service builds an HTTP client for another service: a {@link RestClient} with an explicit connect
 * and read timeout (an HTTP call with no timeout is a thread leak waiting for a slow peer). The timeouts are
 * chosen from the edge inward - see {@code docs/RESILIENCE.md} for the budget.
 */
public final class RestClients {

    private RestClients() {
    }

    public static RestClient.Builder builder(String baseUrl, Duration connectTimeout, Duration readTimeout) {
        HttpClient httpClient = HttpClient.newBuilder().connectTimeout(connectTimeout).build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(readTimeout);
        return RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(requestFactory);
    }
}
