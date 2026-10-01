package com.ecommerce.notificationservice;

import com.ecommerce.common.enums.NotificationStatus;
import com.ecommerce.common.enums.NotificationType;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * A record of a customer notification sent in reaction to a domain event
 * (order created, payment succeeded/failed).
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
