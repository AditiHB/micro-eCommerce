package com.ecommerce.notificationservice;

import com.ecommerce.common.events.OrderCreatedEvent;
import com.ecommerce.common.events.PaymentFailedEvent;
import com.ecommerce.common.events.PaymentProcessedEvent;
import com.ecommerce.common.events.Topics;
import com.ecommerce.common.testsupport.EventSamples;
import com.ecommerce.notificationservice.service.NotificationIntake;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.annotation.KafkaListener;

import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@DisplayName("NotificationEventListener")
class NotificationEventListenerTest {

    private final NotificationIntake intake = mock(NotificationIntake.class);
    private final NotificationEventListener listener = new NotificationEventListener(intake);

    @Test
    @DisplayName("each event is handed to the intake")
    void delegates() {
        OrderCreatedEvent created = EventSamples.orderCreated();
        PaymentProcessedEvent processed = EventSamples.paymentProcessed();
        PaymentFailedEvent failed = EventSamples.paymentFailed();

        listener.handleOrderCreated(created);
        listener.handlePaymentProcessed(processed);
        listener.handlePaymentFailed(failed);

        verify(intake).onOrderCreated(created);
        verify(intake).onPaymentProcessed(processed);
        verify(intake).onPaymentFailed(failed);
    }

    @Test
    @DisplayName("a failure is NOT swallowed - that used to lose the notification - it reaches the container for retry and dead-lettering")
    void failuresPropagate() {
        PaymentProcessedEvent processed = EventSamples.paymentProcessed();
        doThrow(new IllegalStateException("customer service down")).when(intake).onPaymentProcessed(processed);

        assertThatThrownBy(() -> listener.handlePaymentProcessed(processed)).hasMessage("customer service down");
    }

    @Test
    @DisplayName("it listens to the three customer-facing events, in its own group")
    void subscriptions() {
        Map<String, String> topicToGroup = Arrays.stream(NotificationEventListener.class.getDeclaredMethods())
                .map(m -> m.getAnnotation(KafkaListener.class))
                .filter(a -> a != null)
                .collect(Collectors.toMap(a -> a.topics()[0], KafkaListener::groupId));

        assertThat(topicToGroup).containsOnlyKeys(Topics.ORDER_CREATED, Topics.PAYMENT_PROCESSED, Topics.PAYMENT_FAILED);
        assertThat(topicToGroup.values()).containsOnly(Topics.GROUP_NOTIFICATION);
    }
}
