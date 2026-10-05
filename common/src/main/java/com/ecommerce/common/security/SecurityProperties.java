package com.ecommerce.common.security;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * How this service validates the bearer tokens issued by the identity provider.
 *
 * <p>There is deliberately no default for any of the required values: a service that is not told
 * which issuer and audience to trust must refuse to start, not fall back to a value published in
 * the repository. (The previous design signed tokens with an HMAC secret that had a public
 * default, which made every default deployment forgeable.)
 */
@Validated
@ConfigurationProperties(prefix = "ecommerce.security")
@Getter
@Setter
public class SecurityProperties {

    /** JWKS endpoint of the identity provider, as reachable from inside the platform network. */
    @NotBlank
    private String jwkSetUri;

    /**
     * Expected {@code iss} claim. This is the identity provider's public issuer URL and may differ
     * from the host in {@link #jwkSetUri} (internal service name versus public hostname).
     */
    @NotBlank
    private String issuer;

    /** Expected {@code aud} claim: tokens minted for any other API are rejected. */
    @NotBlank
    private String audience;

    /** Claim carrying the id of the customer record a USER is allowed to act on. */
    private String customerIdClaim = "customer_id";

    /** Name of a Spring Boot SSL bundle used to fetch the JWKS over TLS (blank = default trust). */
    private String jwksSslBundle;

    /** Expose Swagger UI / OpenAPI JSON. Off unless a dev profile turns it on. */
    private boolean docsPublic = false;
}
