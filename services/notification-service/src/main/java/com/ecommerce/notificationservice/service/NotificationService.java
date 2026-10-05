package com.ecommerce.notificationservice.service;

import com.ecommerce.common.constants.ApiConstants;
import com.ecommerce.common.dto.PagedResponse;
import com.ecommerce.common.enums.NotificationStatus;
import com.ecommerce.common.enums.NotificationType;
import com.ecommerce.common.exception.BusinessException;
import com.ecommerce.common.exception.ResourceNotFoundException;
import com.ecommerce.common.inbox.InboxService;
import com.ecommerce.common.events.Topics;
import com.ecommerce.notificationservice.Notification;
import com.ecommerce.notificationservice.NotificationRepository;
import com.ecommerce.notificationservice.dto.NotificationResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Set;

/**
 * Builds and stores notifications, and answers queries about them. Delivery is not here: a notification is
 * recorded PENDING in the same transaction as the event that caused it, and {@link NotificationDispatcher} sends it
 * afterwards, with retries.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class NotificationService {

    static final Set<String> SORTABLE_FIELDS = Set.of("id", "createdAt", "updatedAt", "status", "type", "customerId", "orderId");

    private final NotificationRepository notificationRepository;
    private final InboxService inbox;

    // ------------------------------------------------------------------ recording (one transaction per event)

    /**
     * Records the notification for one event. Idempotent: the event is claimed in the inbox first, so a redelivery
     * records nothing a second time.
     *
     * @param recipientEmail where to send it; null or blank if the customer has no usable address, in which case
     *                       the notification is recorded as FAILED with that reason (retrying cannot help)
     */
    public void record(String sourceEventId, Long customerId, Long orderId, NotificationType type,
                       String subject, String message, String recipientEmail) {
        if (!inbox.firstDelivery(Topics.GROUP_NOTIFICATION, sourceEventId)) {
            return;
        }
        Notification.NotificationBuilder builder = Notification.builder()
                .customerId(customerId).orderId(orderId).type(type)
                .subject(subject).message(message).sourceEventId(sourceEventId);

        if (recipientEmail == null || recipientEmail.isBlank()) {
            log.warn("Customer {} has no email address - {} notification for order {} cannot be sent", customerId, type, orderId);
            notificationRepository.save(builder.recipient("unknown").status(NotificationStatus.FAILED)
                    .errorMessage("Could not resolve the customer's email address").build());
            return;
        }
        notificationRepository.save(builder.recipient(recipientEmail).status(NotificationStatus.PENDING)
                .nextAttemptAt(Instant.now()).build());
    }

    public static String orderCreatedSubject(Long orderId) {
        return "Your order #" + orderId + " has been received";
    }

    public static String orderCreatedMessage(Long orderId, int items, BigDecimal total, String currency) {
        return String.format("Hi, thanks for your order! We've received order #%d (%d item%s, total %s %s) and are processing it now.",
                orderId, items, items == 1 ? "" : "s", total, currency);
    }

    public static String paymentSuccessSubject(Long orderId) {
        return "Payment confirmed for order #" + orderId;
    }

    public static String paymentSuccessMessage(Long orderId, BigDecimal amount, String currency) {
        return String.format("Good news! Your payment of %s %s for order #%d was processed successfully.", amount, currency, orderId);
    }

    public static String paymentFailedSubject(Long orderId) {
        return "Payment failed for order #" + orderId;
    }

    public static String paymentFailedMessage(Long orderId, String reason) {
        return String.format("We couldn't process payment for order #%d%s. Your order has been cancelled and any reserved items released. "
                + "Please try again or use a different payment method.", orderId, reason != null ? " (" + reason + ")" : "");
    }

    // ------------------------------------------------------------------ reading

    @Transactional(readOnly = true)
    public NotificationResponse getNotification(Long id) {
        return mapToResponse(notificationRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Notification", id)));
    }

    @Transactional(readOnly = true)
    public List<NotificationResponse> getNotificationsByOrder(Long orderId) {
        return notificationRepository.findByOrderId(orderId).stream().map(this::mapToResponse).toList();
    }

    @Transactional(readOnly = true)
    public PagedResponse<NotificationResponse> getNotificationsByCustomer(Long customerId, int pageNumber, int pageSize) {
        int size = Math.min(Math.max(pageSize, 1), ApiConstants.MAX_PAGE_SIZE);
        Pageable pageable = PageRequest.of(Math.max(pageNumber, 0), size, Sort.by("createdAt").descending());
        Page<Notification> page = notificationRepository.findByCustomerId(customerId, pageable);
        return PagedResponse.of(page.getContent().stream().map(this::mapToResponse).toList(), pageNumber, size, page.getTotalElements());
    }

    @Transactional(readOnly = true)
    public PagedResponse<NotificationResponse> getAllNotifications(int pageNumber, int pageSize, String sortBy) {
        if (!SORTABLE_FIELDS.contains(sortBy)) {
            throw new BusinessException("Cannot sort by '" + sortBy + "'. Allowed: " + SORTABLE_FIELDS, "INVALID_SORT_FIELD");
        }
        int size = Math.min(Math.max(pageSize, 1), ApiConstants.MAX_PAGE_SIZE);
        Pageable pageable = PageRequest.of(Math.max(pageNumber, 0), size, Sort.by(sortBy).descending());
        Page<Notification> page = notificationRepository.findAll(pageable);
        return PagedResponse.of(page.getContent().stream().map(this::mapToResponse).toList(), pageNumber, size, page.getTotalElements());
    }

    NotificationResponse mapToResponse(Notification notification) {
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
