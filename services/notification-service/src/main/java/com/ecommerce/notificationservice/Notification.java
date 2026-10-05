package com.ecommerce.notificationservice;

import com.ecommerce.common.enums.NotificationStatus;
import com.ecommerce.common.enums.NotificationType;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.time.LocalDateTime;

/**
 * A record of a customer notification sent in reaction to a domain event (order created, payment
 * succeeded/failed). It is written PENDING in the same transaction as the event that caused it, and delivered
 * afterwards by the dispatcher with retries - so a mail-server outage neither loses it nor blocks the event stream.
 */
@Entity
@Table(name = "notifications")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
@EqualsAndHashCode(of = "id")
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull(message = "Customer ID cannot be null")
    @Column(nullable = false)
    private Long customerId;

    @Column
    private Long orderId;

    @NotNull(message = "Notification type cannot be null")
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private NotificationType type;

    @NotBlank(message = "Recipient cannot be blank")
    @Column(nullable = false)
    private String recipient;

    @NotBlank(message = "Subject cannot be blank")
    @Column(nullable = false)
    private String subject;

    @NotBlank(message = "Message cannot be blank")
    @Column(nullable = false, length = 2000)
    private String message;

    @NotNull(message = "Status cannot be null")
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private NotificationStatus status;

    @Column(length = 500)
    private String errorMessage;

    /** Delivery attempts made so far; the dispatcher gives up (FAILED) after the configured maximum. */
    @Column(nullable = false)
    @Builder.Default
    private int attempts = 0;

    /** When the dispatcher should next try to send this (PENDING) notification. */
    private Instant nextAttemptAt;

    /**
     * The event that triggered this notification, used for idempotency
     * so the same Kafka event is never notified on twice.
     */
    @Column(nullable = false, unique = true)
    private String sourceEventId;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updatedAt;
}
