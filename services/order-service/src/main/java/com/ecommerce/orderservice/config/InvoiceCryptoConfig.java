package com.ecommerce.orderservice.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

/**
 * Parses the base64-DER key material from {@link InvoiceSigningProperties} and
 * {@link InvoiceDownloadProperties} into real {@link RSAPrivateKey}/{@link RSAPublicKey} beans once at
 * startup, so the batch job, the signer and the download endpoint inject ordinary Spring beans rather
 * than parsing key material themselves.
 */
@Configuration
public class InvoiceCryptoConfig {

    @Bean
    @ConfigurationProperties(prefix = "ecommerce.invoice-signing")
    public InvoiceSigningProperties invoiceSigningProperties() {
        return new InvoiceSigningProperties();
    }

    @Bean
    @ConfigurationProperties(prefix = "ecommerce.invoice-download")
    public InvoiceDownloadProperties invoiceDownloadProperties() {
        return new InvoiceDownloadProperties();
    }

    @Bean
    public RSAPrivateKey invoiceSigningPrivateKey(InvoiceSigningProperties props) throws NoSuchAlgorithmException, InvalidKeySpecException {
        return privateKey(props.getPrivateKey());
    }

    @Bean
    public RSAPublicKey invoiceSigningPublicKey(InvoiceSigningProperties props) throws NoSuchAlgorithmException, InvalidKeySpecException {
        return publicKey(props.getPublicKey());
    }

    @Bean
    public RSAPrivateKey invoiceDownloadPrivateKey(InvoiceDownloadProperties props) throws NoSuchAlgorithmException, InvalidKeySpecException {
        return privateKey(props.getRsaPrivateKey());
    }

    @Bean
    public RSAPublicKey invoiceDownloadPublicKey(InvoiceDownloadProperties props) throws NoSuchAlgorithmException, InvalidKeySpecException {
        return publicKey(props.getRsaPublicKey());
    }

    private static RSAPrivateKey privateKey(String base64Der) throws NoSuchAlgorithmException, InvalidKeySpecException {
        byte[] der = Base64.getDecoder().decode(base64Der);
        return (RSAPrivateKey) KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(der));
    }

    private static RSAPublicKey publicKey(String base64Der) throws NoSuchAlgorithmException, InvalidKeySpecException {
        byte[] der = Base64.getDecoder().decode(base64Der);
        return (RSAPublicKey) KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(der));
    }
}
