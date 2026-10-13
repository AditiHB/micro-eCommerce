package com.ecommerce.orderservice.batch;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("InvoiceSigner")
class InvoiceSignerTest {

    private static InvoiceSigner signer;

    @BeforeAll
    static void generateKeyPair() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair keyPair = generator.generateKeyPair();
        signer = new InvoiceSigner((RSAPrivateKey) keyPair.getPrivate(), (RSAPublicKey) keyPair.getPublic());
    }

    @Test
    @DisplayName("a signature verifies against the exact bytes it was computed over")
    void signAndVerify() {
        byte[] pdf = "the canonical pdf bytes".getBytes(StandardCharsets.UTF_8);

        byte[] signature = signer.sign(pdf);

        assertThat(signer.verify(pdf, signature)).isTrue();
    }

    @Test
    @DisplayName("tampering with even one byte fails verification")
    void tamperedContentFailsVerification() {
        byte[] pdf = "the canonical pdf bytes".getBytes(StandardCharsets.UTF_8);
        byte[] signature = signer.sign(pdf);
        byte[] tampered = pdf.clone();
        tampered[0] ^= 1;

        assertThat(signer.verify(tampered, signature)).isFalse();
    }

    @Test
    @DisplayName("sha256Hex is deterministic and matches a known vector")
    void sha256HexIsDeterministic() {
        assertThat(InvoiceSigner.sha256Hex("".getBytes(StandardCharsets.UTF_8)))
                .isEqualTo("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855");
    }
}
