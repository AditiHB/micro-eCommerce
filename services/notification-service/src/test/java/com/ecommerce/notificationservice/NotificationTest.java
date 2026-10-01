package com.ecommerce.notificationservice;

import com.ecommerce.common.enums.NotificationStatus;
import com.ecommerce.common.enums.NotificationType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Notification Entity Tests")
class NotificationTest {

    @Test
    @DisplayName("Should build a notification with all fields set via builder")
    void testBuilderSetsAllFields() {
        Notification notification = Notification.builder()
            .id(1L)
            .customerId(10L)
            .orderId(100L)
            .type(NotificationType.ORDER_CREATED)
            .recipient("customer@example.com")
            .subject("Order received")
            .message("We received your order")
            .status(NotificationStatus.SENT)
            .sourceEventId("evt-1")
            .build();

        assertThat(notification.getId()).isEqualTo(1L);
        assertThat(notification.getCustomerId()).isEqualTo(10L);
        assertThat(notification.getOrderId()).isEqualTo(100L);
        assertThat(notification.getType()).isEqualTo(NotificationType.ORDER_CREATED);
        assertThat(notification.getRecipient()).isEqualTo("customer@example.com");
        assertThat(notification.getStatus()).isEqualTo(NotificationStatus.SENT);
        assertThat(notification.getSourceEventId()).isEqualTo("evt-1");
    }

    @Test
    @DisplayName("Two notifications with the same id should be equal")
    void testEqualsAndHashCodeById() {
        Notification a = Notification.builder().id(5L).build();
        Notification b = Notification.builder().id(5L).build();

        assertThat(a).isEqualTo(b);
        assertThat(a.hashCode()).isEqualTo(b.hashCode());
    }
}
