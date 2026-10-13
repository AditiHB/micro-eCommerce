package com.ecommerce.customerservice.crypto;

import com.ecommerce.customerservice.config.CardDigitizationProperties;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.util.Base64;

/**
 * Makes the card-digitization key reachable from {@link CardCiphertextConverter}, which Hibernate
 * instantiates itself and never wires through Spring's {@code ApplicationContext}. A static holder,
 * populated once by this ordinary constructor-injected bean, is simpler and more predictable here than
 * relying on Hibernate's Spring-bean-container integration (which bootstraps relative to the persistence
 * unit in ways that are easy to get subtly wrong).
 *
 * <p>Spring constructs this bean - and therefore populates the key - before any repository can perform a
 * JPA write, so {@link #get()} is never called before {@link #INSTANCE} is set in a correctly started
 * application context.
 */
@Component
public class CardEncryptionKeyHolder {

    private static volatile SecretKey INSTANCE;

    public CardEncryptionKeyHolder(CardDigitizationProperties properties) {
        byte[] keyBytes = Base64.getDecoder().decode(properties.getEncryptionKey());
        INSTANCE = new SecretKeySpec(keyBytes, "AES");
    }

    public static SecretKey get() {
        SecretKey key = INSTANCE;
        if (key == null) {
            throw new IllegalStateException(
                    "Card encryption key was read before CardEncryptionKeyHolder was constructed by Spring");
        }
        return key;
    }
}
