package com.ecommerce.customerservice;

import com.ecommerce.customerservice.crypto.CardCiphertextConverter;
import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "customers")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
@EqualsAndHashCode(of = "id")
public class Customer {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "Name cannot be blank")
    @Size(min = 2, max = 100, message = "Name must be between 2 and 100 characters")
    @Column(nullable = false)
    private String name;

    @NotBlank(message = "Email is required")
    @Email(message = "Email should be valid")
    @Column(nullable = false, unique = true)
    private String email;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    /** Optimistic lock and the resource's ETag. */
    @Version
    private Long version;

    /** One card on file, at most. Holds the plain number in memory; {@link CardCiphertextConverter}
     *  JWE-encrypts it on the way to the {@code card_ciphertext} column and decrypts it on the way back. */
    @Convert(converter = CardCiphertextConverter.class)
    @Column(name = "card_ciphertext")
    private String cardNumber;

    /** Not sensitive on its own (PCI-DSS allows a truncated PAN in the clear) - kept in plain text so a
     *  response can show "card ending in 4242" without decrypting on every read. */
    @Column(name = "card_last_four", length = 4)
    private String cardLastFour;

    /** Sets (or replaces) the card on file, deriving the plaintext last-4 digits shown in responses. */
    public void setCard(String fullCardNumber) {
        this.cardNumber = fullCardNumber;
        this.cardLastFour = fullCardNumber.substring(fullCardNumber.length() - 4);
    }

    /** Removes the card on file. */
    public void clearCard() {
        this.cardNumber = null;
        this.cardLastFour = null;
    }
}
