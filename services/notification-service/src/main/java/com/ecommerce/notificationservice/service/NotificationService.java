package com.ecommerce.notificationservice.service;

import com.ecommerce.common.config.CacheConfig;
import com.ecommerce.common.constants.ApiConstants;
import com.ecommerce.common.dto.PagedResponse;
import com.ecommerce.common.enums.NotificationStatus;
import com.ecommerce.common.enums.NotificationType;
import com.ecommerce.common.exception.ResourceNotFoundException;
import com.ecommerce.notificationservice.Notification;
import com.ecommerce.notificationservice.NotificationRepository;
import com.ecommerce.notificationservice.client.CustomerClient;
import com.ecommerce.notificationservice.client.CustomerInfo;
import com.ecommerce.notificationservice.dto.NotificationResponse;
import com.ecommerce.notificationservice.sender.NotificationDeliveryException;
import com.ecommerce.notificationservice.sender.NotificationSender;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/**
 * Builds, persists and dispatches customer-facing notifications in
 * response to domain events (order created, payment processed/failed).
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final CustomerClient customerClient;
    private final NotificationSender notificationSender;

    /**
     * Notify a customer that their order was created.
     */
    @CacheEvict(value = CacheConfig.NOTIFICATIONS_CACHE, allEntries = true)
    public void notifyOrderCreated(String sourceEventId, Long orderId, Long customerId,
                                    String productId, Integer quantity) {
        String subject = "Your order #" + orderId + " has been received";
        String message = String.format(
            "Hi, thanks for your order! We've received order #%d for %d x %s and are processing it now.",
            orderId, quantity, productId);

        notify(sourceEventId, customerId, orderId, NotificationType.ORDER_CREATED, subject, message);
    }

    /**
     * Notify a customer that payment for their order succeeded.
     */
    @CacheEvict(value = CacheConfig.NOTIFICATIONS_CACHE, allEntries = true)
    public void notifyPaymentSuccess(String sourceEventId, Long orderId, Long customerId, BigDecimal amount) {
        String subject = "Payment confirmed for order #" + orderId;
        String message = String.format(
            "Good news! Your payment of %s for order #%d was processed successfully.",
            amount, orderId);

        notify(sourceEventId, customerId, orderId, NotificationType.PAYMENT_SUCCESS, subject, message);
    }

    /**
     * Notify a customer that payment for their order failed.
     */
    @CacheEvict(value = CacheConfig.NOTIFICATIONS_CACHE, allEntries = true)
    public void notifyPaymentFailed(String sourceEventId, Long orderId, Long customerId, String reason) {
        String subject = "Payment failed for order #" + orderId;
        String message = String.format(
            "We couldn't process payment for order #%d%s. Your order has been cancelled and any reserved items released. " +
                "Please try again or use a different payment method.",
            orderId, reason != null ? " (" + reason + ")" : "");

        notify(sourceEventId, customerId, orderId, NotificationType.PAYMENT_FAILED, subject, message);
    }

    /**
     * Core notify flow: idempotency check, customer lookup, persistence and dispatch.
     */
    private void notify(String sourceEventId, Long customerId, Long orderId, NotificationType type,
                         String subject, String message) {
        if (sourceEventId != null && notificationRepository.existsBySourceEventId(sourceEventId)) {
            log.info("Notification for event {} already processed, skipping (idempotency)", sourceEventId);
            return;
        }

        Optional<CustomerInfo> customer = customerClient.getCustomer(customerId);

        Notification.NotificationBuilder builder = Notification.builder()
            .customerId(customerId)
            .orderId(orderId)
            .type(type)
            .subject(subject)
            .message(message)
            .sourceEventId(sourceEventId != null ? sourceEventId : java.util.UUID.randomUUID().toString());

        if (customer.isEmpty() || customer.get().getEmail() == null || customer.get().getEmail().isBlank()) {
            log.warn("Unable to resolve an email for customer {} - notification not sent", customerId);
            Notification notification = builder
                .recipient("unknown")
                .status(NotificationStatus.FAILED)
                .errorMessage("Could not resolve customer's email address")
                .build();
            notificationRepository.save(notification);
            return;
        }

        String recipient = customer.get().getEmail();
        Notification.NotificationBuilder resolved = builder.recipient(recipient);

        try {
            notificationSender.send(recipient, subject, message);
            notificationRepository.save(resolved.status(NotificationStatus.SENT).build());
            log.info("✓ {} notification sent to {} for order {}", type, recipient, orderId);
        } catch (NotificationDeliveryException e) {
            log.error("✗ Failed to deliver {} notification to {} for order {}: {}",
                type, recipient, orderId, e.getMessage());
            notificationRepository.save(resolved
                .status(NotificationStatus.FAILED)
                .errorMessage(e.getMessage())
                .build());
        }
    }

    @Transactional(readOnly = true)
    public NotificationResponse getNotification(Long id) {
        Notification notification = notificationRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Notification", id));
        return mapToResponse(notification);
    }

    @Transactional(readOnly = true)
    public List<NotificationResponse> getNotificationsByOrder(Long orderId) {
        return notificationRepository.findByOrderId(orderId).stream()
            .map(this::mapToResponse)
            .toList();
    }

    @Transactional(readOnly = true)
    @Cacheable(value = CacheConfig.NOTIFICATIONS_CACHE,
        key = "'customer:' + #customerId + ':' + #pageNumber + ':' + #pageSize")
    public PagedResponse<NotificationResponse> getNotificationsByCustomer(Long customerId, int pageNumber, int pageSize) {
        pageSize = Math.min(pageSize, ApiConstants.MAX_PAGE_SIZE);
        Pageable pageable = PageRequest.of(pageNumber, pageSize, Sort.by("createdAt").descending());
        Page<Notification> page = notificationRepository.findByCustomerId(customerId, pageable);

        List<NotificationResponse> responses = page.getContent().stream()
            .map(this::mapToResponse)
            .toList();

        return PagedResponse.of(responses, pageNumber, pageSize, page.getTotalElements());
    }

    @Transactional(readOnly = true)
    public PagedResponse<NotificationResponse> getAllNotifications(int pageNumber, int pageSize, String sortBy) {
        pageSize = Math.min(pageSize, ApiConstants.MAX_PAGE_SIZE);
        Pageable pageable = PageRequest.of(pageNumber, pageSize, Sort.by(sortBy).descending());
        Page<Notification> page = notificationRepository.findAll(pageable);

        List<NotificationResponse> responses = page.getContent().stream()
            .map(this::mapToResponse)
            .toList();

        return PagedResponse.of(responses, pageNumber, pageSize, page.getTotalElements());
    }

    private NotificationResponse mapToResponse(Notification notification) {
        return NotificationResponse.builder()
            .id(notification.getId())
            .customerId(notification.getCustomerId())
            .orderId(notification.getOrderId())
            .type(notification.getType())
            .recipient(notification.getRecipient())
            .subject(notification.getSubject())
            .message(notification.getMessage())
            .status(notification.getStatus())
            .errorMessage(notification.getErrorMessage())
            .createdAt(notification.getCreatedAt())
            .updatedAt(notification.getUpdatedAt())
            .build();
    }
}
