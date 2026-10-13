package com.ecommerce.orderservice.config;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import org.springframework.validation.annotation.Validated;

/**
 * The RSA keypair that signs (private key) and verifies (public key) every generated invoice PDF.
 * Each half is base64-encoded DER - not PEM - so it drops into a single-line environment variable or
 * Vault value with no header/footer/newline handling needed. Dedicated to invoices; not the download
 * endpoint's key ({@link InvoiceDownloadProperties}) and not {@code infrastructure/pki}'s TLS CA.
 */
@Validated
@Getter
@Setter
public class InvoiceSigningProperties {

    @NotBlank
    private String privateKey;
    @NotBlank
    private String publicKey;
}
