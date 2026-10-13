package com.ecommerce.orderservice.config;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import org.springframework.validation.annotation.Validated;

/**
 * The RSA keypair the invoice download endpoint encrypts (JWE, public key) and - for whoever holds the
 * matching private key - decrypts the response with. A single platform-wide keypair, not a per-customer
 * one: a deliberate simplification for this worked example, not a real key-exchange system. Base64 DER,
 * like {@link InvoiceSigningProperties}.
 */
@Validated
@Getter
@Setter
public class InvoiceDownloadProperties {

    @NotBlank
    private String rsaPrivateKey;
    @NotBlank
    private String rsaPublicKey;
}
