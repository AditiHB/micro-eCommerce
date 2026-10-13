package com.ecommerce.orderservice.batch;

import org.springframework.stereotype.Component;

import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.Signature;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.HexFormat;

/**
 * Signs and verifies the canonical, <em>uncompressed</em> PDF bytes - never a compressed variant, so
 * verification never depends on which of the three stored copies is at hand.
 */
@Component
public class InvoiceSigner {

    public static final String SIGNING_KEY_ID = "invoice-signing-v1";

    private final RSAPrivateKey privateKey;
    private final RSAPublicKey publicKey;

    public InvoiceSigner(RSAPrivateKey invoiceSigningPrivateKey, RSAPublicKey invoiceSigningPublicKey) {
        this.privateKey = invoiceSigningPrivateKey;
        this.publicKey = invoiceSigningPublicKey;
    }

    public byte[] sign(byte[] canonicalPdfBytes) {
        try {
            Signature signature = Signature.getInstance("SHA256withRSA");
            signature.initSign(privateKey);
            signature.update(canonicalPdfBytes);
            return signature.sign();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Failed to sign invoice PDF", e);
        }
    }

    public boolean verify(byte[] canonicalPdfBytes, byte[] signatureBytes) {
        try {
            Signature signature = Signature.getInstance("SHA256withRSA");
            signature.initVerify(publicKey);
            signature.update(canonicalPdfBytes);
            return signature.verify(signatureBytes);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Failed to verify invoice PDF signature", e);
        }
    }

    public static String sha256Hex(byte[] data) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
