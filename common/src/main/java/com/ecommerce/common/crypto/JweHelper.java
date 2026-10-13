package com.ecommerce.common.crypto;

import com.ecommerce.common.exception.EncryptionException;
import com.nimbusds.jose.EncryptionMethod;
import com.nimbusds.jose.JWEAlgorithm;
import com.nimbusds.jose.JWEHeader;
import com.nimbusds.jose.JWEObject;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.Payload;
import com.nimbusds.jose.crypto.AESDecrypter;
import com.nimbusds.jose.crypto.AESEncrypter;
import com.nimbusds.jose.crypto.RSADecrypter;
import com.nimbusds.jose.crypto.RSAEncrypter;

import javax.crypto.SecretKey;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.text.ParseException;

/**
 * Encrypts/decrypts a byte payload into the standard five-part JWE compact serialization
 * ({@code header.encryptedKey.iv.ciphertext.tag}).
 *
 * <p>Two recipes are supported, picked for who holds the key on each side:
 * <ul>
 *   <li><b>Symmetric ({@code A256KW} + {@code A256GCM})</b> - the same party encrypts and later decrypts
 *   its own data (customer-service's card-on-file column). One 256-bit key wraps a fresh per-call content
 *   encryption key.</li>
 *   <li><b>Asymmetric ({@code RSA-OAEP-256} + {@code A256GCM})</b> - the encrypter and the eventual
 *   decrypter are different parties (order-service encrypts an invoice download for whoever holds the
 *   matching private key). Only the public key is needed to encrypt.</li>
 * </ul>
 */
public final class JweHelper {

    private JweHelper() {
    }

    public static String encryptCompact(byte[] plaintext, SecretKey key) {
        try {
            JWEHeader header = new JWEHeader(JWEAlgorithm.A256KW, EncryptionMethod.A256GCM);
            JWEObject jwe = new JWEObject(header, new Payload(plaintext));
            jwe.encrypt(new AESEncrypter(key));
            return jwe.serialize();
        } catch (JOSEException e) {
            throw new EncryptionException("Failed to JWE-encrypt payload", e);
        }
    }

    public static byte[] decryptCompact(String compact, SecretKey key) {
        try {
            JWEObject jwe = JWEObject.parse(compact);
            jwe.decrypt(new AESDecrypter(key));
            return jwe.getPayload().toBytes();
        } catch (ParseException | JOSEException e) {
            throw new EncryptionException("Failed to JWE-decrypt payload", e);
        }
    }

    public static String encryptCompact(byte[] plaintext, RSAPublicKey key) {
        try {
            JWEHeader header = new JWEHeader(JWEAlgorithm.RSA_OAEP_256, EncryptionMethod.A256GCM);
            JWEObject jwe = new JWEObject(header, new Payload(plaintext));
            jwe.encrypt(new RSAEncrypter(key));
            return jwe.serialize();
        } catch (JOSEException e) {
            throw new EncryptionException("Failed to JWE-encrypt payload", e);
        }
    }

    public static byte[] decryptCompact(String compact, RSAPrivateKey key) {
        try {
            JWEObject jwe = JWEObject.parse(compact);
            jwe.decrypt(new RSADecrypter(key));
            return jwe.getPayload().toBytes();
        } catch (ParseException | JOSEException e) {
            throw new EncryptionException("Failed to JWE-decrypt payload", e);
        }
    }
}
