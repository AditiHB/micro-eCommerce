package com.ecommerce.notificationservice.dto;

import com.ecommerce.common.enums.NotificationStatus;
import com.ecommerce.common.enums.NotificationType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@AllArgsConstructor
@Builder
public class NotificationResponse {
    private Long id;
    private Long customerId;
    private Long orderId;
    private NotificationType type;
    private String recipient;
    private String subject;
    private String message;
    private NotificationStatus status;
    private String errorMessage;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
