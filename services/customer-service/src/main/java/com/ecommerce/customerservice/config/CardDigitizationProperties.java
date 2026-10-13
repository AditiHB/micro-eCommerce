package com.ecommerce.customerservice.config;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import org.springframework.validation.annotation.Validated;

/**
 * The symmetric key that encrypts the card-on-file column (see {@code CardCiphertextConverter}). Base64 of
 * 32 random bytes, injected from Vault or the environment - never defaulted, so a service started without
 * it fails fast instead of silently running with no encryption.
 */
@Validated
@Getter
@Setter
public class CardDigitizationProperties {

    @NotBlank
    private String encryptionKey;
}
