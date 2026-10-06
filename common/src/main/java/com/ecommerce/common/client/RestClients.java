package com.ecommerce.common.client;

import org.springframework.boot.ssl.SslBundle;
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
        return builder(baseUrl, connectTimeout, readTimeout, null);
    }

    /**
     * Same as {@link #builder(String, Duration, Duration)}, but presents {@code sslBundle}'s keystore as this
     * client's own certificate and trusts only its truststore - mutual TLS to a peer that requires a client
     * certificate. Pass {@code null} (or use the 3-arg overload) when the callee doesn't require one, e.g. the
     * {@code h2}/{@code postgres} profiles where the {@code mtls} bundle was never configured.
     */
    public static RestClient.Builder builder(String baseUrl, Duration connectTimeout, Duration readTimeout,
                                               SslBundle sslBundle) {
        HttpClient.Builder httpClientBuilder = HttpClient.newBuilder().connectTimeout(connectTimeout);
        if (sslBundle != null) {
            httpClientBuilder.sslContext(sslBundle.createSslContext());
        }
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClientBuilder.build());
        requestFactory.setReadTimeout(readTimeout);
        return RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(requestFactory);
    }
}
