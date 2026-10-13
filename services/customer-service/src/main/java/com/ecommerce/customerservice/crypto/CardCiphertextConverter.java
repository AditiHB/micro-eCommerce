package com.ecommerce.customerservice.crypto;

import com.ecommerce.common.crypto.JweHelper;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.nio.charset.StandardCharsets;

/**
 * Persists the card number as a JWE compact serialization ({@code header.encryptedKey.iv.ciphertext.tag})
 * instead of plain text. {@code autoApply = false}: this only ever applies where {@code @Convert}
 * explicitly says so, never implicitly to every {@code String} field on every entity.
 */
@Converter(autoApply = false)
public class CardCiphertextConverter implements AttributeConverter<String, String> {

    @Override
    public String convertToDatabaseColumn(String plainCardNumber) {
        if (plainCardNumber == null) {
            return null;
        }
        return JweHelper.encryptCompact(plainCardNumber.getBytes(StandardCharsets.UTF_8), CardEncryptionKeyHolder.get());
    }

    @Override
    public String convertToEntityAttribute(String jweCompact) {
        if (jweCompact == null) {
            return null;
        }
        return new String(JweHelper.decryptCompact(jweCompact, CardEncryptionKeyHolder.get()), StandardCharsets.UTF_8);
    }
}
