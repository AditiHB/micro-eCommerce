package com.ecommerce.notificationservice.service;

import com.ecommerce.common.enums.NotificationStatus;
import com.ecommerce.common.enums.NotificationType;
import com.ecommerce.notificationservice.Notification;
import com.ecommerce.notificationservice.NotificationRepository;
import com.ecommerce.notificationservice.client.CustomerClient;
import com.ecommerce.notificationservice.client.CustomerInfo;
import com.ecommerce.notificationservice.sender.NotificationDeliveryException;
import com.ecommerce.notificationservice.sender.NotificationSender;
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
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("NotificationService Unit Tests")
class NotificationServiceTest {

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private CustomerClient customerClient;

    @Mock
    private NotificationSender notificationSender;

    private NotificationService notificationService;

    private CustomerInfo customer;

    @BeforeEach
    void setUp() {
        notificationService = new NotificationService(notificationRepository, customerClient, notificationSender);
        customer = new CustomerInfo(10L, "Jane Doe", "jane@example.com");
    }

    @Test
    @DisplayName("Should send and save a SENT notification when order is created")
    void testNotifyOrderCreatedSuccess() {
        when(notificationRepository.existsBySourceEventId("evt-1")).thenReturn(false);
        when(customerClient.getCustomer(10L)).thenReturn(Optional.of(customer));

        notificationService.notifyOrderCreated("evt-1", 100L, 10L, "PROD-001", 2);

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(captor.capture());
        verify(notificationSender).send(eq("jane@example.com"), anyString(), anyString());

        Notification saved = captor.getValue();
        assertThat(saved.getStatus()).isEqualTo(NotificationStatus.SENT);
        assertThat(saved.getType()).isEqualTo(NotificationType.ORDER_CREATED);
        assertThat(saved.getRecipient()).isEqualTo("jane@example.com");
        assertThat(saved.getCustomerId()).isEqualTo(10L);
        assertThat(saved.getOrderId()).isEqualTo(100L);
    }

    @Test
    @DisplayName("Should skip notification when the source event was already processed")
    void testIdempotencySkipsDuplicateEvent() {
        when(notificationRepository.existsBySourceEventId("evt-dup")).thenReturn(true);

        notificationService.notifyOrderCreated("evt-dup", 100L, 10L, "PROD-001", 2);

        verifyNoInteractions(customerClient, notificationSender);
        verify(notificationRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should record a FAILED notification when the customer's email can't be resolved")
    void testNotifyWhenCustomerNotFound() {
        when(notificationRepository.existsBySourceEventId("evt-2")).thenReturn(false);
        when(customerClient.getCustomer(10L)).thenReturn(Optional.empty());

        notificationService.notifyPaymentSuccess("evt-2", 100L, 10L, new BigDecimal("49.99"));

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(captor.capture());
        verifyNoInteractions(notificationSender);

        Notification saved = captor.getValue();
        assertThat(saved.getStatus()).isEqualTo(NotificationStatus.FAILED);
        assertThat(saved.getErrorMessage()).isNotBlank();
    }

    @Test
    @DisplayName("Should record a FAILED notification when delivery throws")
    void testNotifyWhenDeliveryFails() {
        when(notificationRepository.existsBySourceEventId("evt-3")).thenReturn(false);
        when(customerClient.getCustomer(10L)).thenReturn(Optional.of(customer));
        doThrow(new NotificationDeliveryException("SMTP down"))
            .when(notificationSender).send(anyString(), anyString(), anyString());

        notificationService.notifyPaymentFailed("evt-3", 100L, 10L, "Card declined");

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(captor.capture());

        Notification saved = captor.getValue();
        assertThat(saved.getStatus()).isEqualTo(NotificationStatus.FAILED);
        assertThat(saved.getErrorMessage()).isEqualTo("SMTP down");
        assertThat(saved.getType()).isEqualTo(NotificationType.PAYMENT_FAILED);
    }
}
