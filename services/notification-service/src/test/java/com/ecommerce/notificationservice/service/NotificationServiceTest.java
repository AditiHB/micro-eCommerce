package com.ecommerce.notificationservice.service;

import com.ecommerce.common.enums.NotificationStatus;
import com.ecommerce.common.enums.NotificationType;
import com.ecommerce.common.exception.BusinessException;
import com.ecommerce.common.exception.ResourceNotFoundException;
import com.ecommerce.common.events.Topics;
import com.ecommerce.common.inbox.InboxService;
import com.ecommerce.notificationservice.Notification;
import com.ecommerce.notificationservice.NotificationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("NotificationService")
class NotificationServiceTest {

    @Mock
    private NotificationRepository repository;
    @Mock
    private InboxService inbox;

    private NotificationService service;

    @BeforeEach
    void setUp() {
        service = new NotificationService(repository, inbox);
    }

    private Notification recorded() {
        ArgumentCaptor<Notification> saved = ArgumentCaptor.forClass(Notification.class);
        verify(repository).save(saved.capture());
        return saved.getValue();
    }

    @Test
    @DisplayName("a notification with an address is recorded PENDING and due immediately, for the dispatcher to send")
    void recordsPending() {
        when(inbox.firstDelivery(Topics.GROUP_NOTIFICATION, "evt-1")).thenReturn(true);

        service.record("evt-1", 7L, 42L, NotificationType.PAYMENT_SUCCESS, "Subject", "Body", "jane@example.com");

        Notification notification = recorded();
        assertThat(notification.getStatus()).isEqualTo(NotificationStatus.PENDING);
        assertThat(notification.getRecipient()).isEqualTo("jane@example.com");
        assertThat(notification.getAttempts()).isZero();
        assertThat(notification.getNextAttemptAt()).isNotNull();
        assertThat(notification.getSourceEventId()).isEqualTo("evt-1");
    }

    @Test
    @DisplayName("without an address it is recorded FAILED straight away - retrying cannot conjure an email")
    void noAddress() {
        when(inbox.firstDelivery(any(), any())).thenReturn(true);

        service.record("evt-1", 7L, 42L, NotificationType.ORDER_CREATED, "Subject", "Body", " ");

        Notification notification = recorded();
        assertThat(notification.getStatus()).isEqualTo(NotificationStatus.FAILED);
        assertThat(notification.getErrorMessage()).contains("email address");
    }

    @Test
    @DisplayName("a redelivered event records nothing a second time (inbox)")
    void duplicate() {
        when(inbox.firstDelivery(any(), eq("evt-1"))).thenReturn(false);

        service.record("evt-1", 7L, 42L, NotificationType.ORDER_CREATED, "S", "B", "jane@example.com");

        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("message texts carry the order, the item count, and the amount with its currency")
    void messages() {
        assertThat(NotificationService.orderCreatedSubject(42L)).isEqualTo("Your order #42 has been received");
        assertThat(NotificationService.orderCreatedMessage(42L, 2, new BigDecimal("172.97"), "USD"))
                .contains("#42").contains("2 items").contains("172.97 USD");
        assertThat(NotificationService.orderCreatedMessage(42L, 1, BigDecimal.TEN, "EUR")).contains("1 item,");
        assertThat(NotificationService.paymentSuccessMessage(42L, new BigDecimal("50.00"), "GBP")).contains("50.00 GBP");
        assertThat(NotificationService.paymentFailedMessage(42L, "Card declined")).contains("(Card declined)");
        assertThat(NotificationService.paymentFailedMessage(42L, null)).doesNotContain("(");
    }

    @Test
    @DisplayName("an unknown notification is a 404; sorting is limited to known fields")
    void reading() {
        when(repository.findById(9L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getNotification(9L)).isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> service.getAllNotifications(0, 10, "recipient"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode()).isEqualTo("INVALID_SORT_FIELD");
    }
}
