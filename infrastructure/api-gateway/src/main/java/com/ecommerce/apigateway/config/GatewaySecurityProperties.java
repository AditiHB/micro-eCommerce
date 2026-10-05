package com.ecommerce.apigateway.config;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Which tokens the edge accepts. Same contract as the services' {@code ecommerce.security.*}: no
 * shared secret, nothing defaulted that a deployer could forget to override.
 */
@Validated
@ConfigurationProperties(prefix = "ecommerce.security")
@Getter
@Setter
public class GatewaySecurityProperties {

    /** JWKS endpoint of the identity provider, as reachable from the gateway. */
    @NotBlank
    private String jwkSetUri;

    /** Expected {@code iss} claim (the identity provider's public issuer URL). */
    @NotBlank
    private String issuer;

    /** Expected {@code aud} claim. */
    @NotBlank
    private String audience;

    /** Name of a Spring Boot SSL bundle used to fetch the JWKS over TLS (blank = default trust). */
    private String jwksSslBundle;
}
