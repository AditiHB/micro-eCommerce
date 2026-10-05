package com.ecommerce.notificationservice.service;

import com.ecommerce.common.enums.NotificationType;
import com.ecommerce.common.events.PaymentProcessedEvent;
import com.ecommerce.common.exception.NonRetryableEventException;
import com.ecommerce.common.testsupport.EventSamples;
import com.ecommerce.notificationservice.NotificationRepository;
import com.ecommerce.notificationservice.client.CustomerClient;
import com.ecommerce.notificationservice.client.CustomerInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.ResourceAccessException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("NotificationIntake")
class NotificationIntakeTest {

    @Mock
    private NotificationService notifications;
    @Mock
    private NotificationRepository repository;
    @Mock
    private CustomerClient customers;

    private NotificationIntake intake;

    @BeforeEach
    void setUp() {
        intake = new NotificationIntake(notifications, repository, customers);
    }

    @Test
    @DisplayName("the customer id comes from the event itself - no call to the order service - and only the email is looked up")
    void usesTheCustomerFromTheEvent() {
        PaymentProcessedEvent event = EventSamples.paymentProcessed();
        when(repository.existsBySourceEventId(event.getEventId())).thenReturn(false);
        when(customers.find(7L)).thenReturn(Optional.of(new CustomerInfo(7L, "Jane", "jane@example.com")));

        intake.onPaymentProcessed(event);

        verify(notifications).record(eq(event.getEventId()), eq(7L), eq(42L), eq(NotificationType.PAYMENT_SUCCESS),
                eq("Payment confirmed for order #42"), anyString(), eq("jane@example.com"));
    }

    @Test
    @DisplayName("an unknown customer is recorded as a failed notification (no address), not retried forever")
    void unknownCustomer() {
        when(repository.existsBySourceEventId(anyString())).thenReturn(false);
        when(customers.find(7L)).thenReturn(Optional.empty());

        intake.onPaymentFailed(EventSamples.paymentFailed());

        verify(notifications).record(anyString(), eq(7L), eq(42L), eq(NotificationType.PAYMENT_FAILED), anyString(), anyString(), eq(null));
    }

    @Test
    @DisplayName("a customer service outage is NOT turned into 'no customer': the exception propagates for retry")
    void customerServiceOutagePropagates() {
        when(repository.existsBySourceEventId(anyString())).thenReturn(false);
        when(customers.find(7L)).thenThrow(new ResourceAccessException("read timed out"));

        assertThatThrownBy(() -> intake.onOrderCreated(EventSamples.orderCreated())).isInstanceOf(ResourceAccessException.class);

        verify(notifications, never()).record(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("an event already recorded is skipped before any remote call is made")
    void duplicateIsSkippedCheaply() {
        when(repository.existsBySourceEventId(anyString())).thenReturn(true);

        intake.onOrderCreated(EventSamples.orderCreated());

        verifyNoInteractions(customers);
        verify(notifications, never()).record(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("an event that names no customer can never be notified: dead-letter it, do not retry")
    void noCustomerOnTheEvent() {
        PaymentProcessedEvent event = EventSamples.paymentProcessed();
        event.setCustomerId(null);

        assertThatThrownBy(() -> intake.onPaymentProcessed(event)).isInstanceOf(NonRetryableEventException.class);
    }
}
