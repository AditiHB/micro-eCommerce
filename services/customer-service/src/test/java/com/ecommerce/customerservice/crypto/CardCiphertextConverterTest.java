package com.ecommerce.customerservice.crypto;

import com.ecommerce.customerservice.config.CardDigitizationProperties;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("CardCiphertextConverter")
class CardCiphertextConverterTest {

    private final CardCiphertextConverter converter = new CardCiphertextConverter();

    @BeforeAll
    static void initKey() {
        CardDigitizationProperties properties = new CardDigitizationProperties();
        properties.setEncryptionKey(Base64.getEncoder().encodeToString("0123456789abcdef0123456789abcdef".getBytes()));
        new CardEncryptionKeyHolder(properties);
    }

    @Test
    @DisplayName("a null card number is stored and read back as null")
    void nullRoundTrip() {
        assertThat(converter.convertToDatabaseColumn(null)).isNull();
        assertThat(converter.convertToEntityAttribute(null)).isNull();
    }

    @Test
    @DisplayName("the stored value is a real JWE compact serialization, not the plaintext card number")
    void encryptsToJweCompactSerialization() {
        String ciphertext = converter.convertToDatabaseColumn("4242424242424242");

        assertThat(ciphertext).isNotEqualTo("4242424242424242");
        assertThat(ciphertext.split("\\.")).hasSize(5);
    }

    @Test
    @DisplayName("decrypting the stored value returns exactly the original card number")
    void roundTrips() {
        String ciphertext = converter.convertToDatabaseColumn("4242424242424242");

        assertThat(converter.convertToEntityAttribute(ciphertext)).isEqualTo("4242424242424242");
    }
}
