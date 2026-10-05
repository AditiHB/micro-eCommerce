package com.ecommerce.orderservice;

import com.ecommerce.common.events.InventoryReservedEvent;
import com.ecommerce.common.events.Topics;
import com.ecommerce.common.testsupport.EventSamples;
import com.ecommerce.orderservice.service.OrderSagaHandler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.annotation.KafkaListener;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@DisplayName("OrderEventListener")
class OrderEventListenerTest {

    private final OrderSagaHandler handler = mock(OrderSagaHandler.class);
    private final OrderEventListener listener = new OrderEventListener(handler);

    @Test
    @DisplayName("each saga event is handed to the transactional handler")
    void delegates() {
        var reserved = EventSamples.inventoryReserved();
        var failed = EventSamples.inventoryFailed();
        var processed = EventSamples.paymentProcessed();
        var paymentFailed = EventSamples.paymentFailed();
        var refunded = EventSamples.refundCompleted();

        listener.handleInventoryReserved(reserved);
        listener.handleInventoryFailed(failed);
        listener.handlePaymentProcessed(processed);
        listener.handlePaymentFailed(paymentFailed);
        listener.handleRefundCompleted(refunded);

        verify(handler).onInventoryReserved(reserved);
        verify(handler).onInventoryFailed(failed);
        verify(handler).onPaymentProcessed(processed);
        verify(handler).onPaymentFailed(paymentFailed);
        verify(handler).onRefundCompleted(refunded);
    }

    @Test
    @DisplayName("a failing handler is NOT swallowed: the exception reaches the container so it can retry and dead-letter")
    void failuresPropagate() {
        InventoryReservedEvent event = EventSamples.inventoryReserved();
        doThrow(new IllegalStateException("database blip")).when(handler).onInventoryReserved(event);

        assertThatThrownBy(() -> listener.handleInventoryReserved(event)).hasMessage("database blip");
    }

    @Test
    @DisplayName("it listens to exactly the topics of the saga steps it reacts to, in its own consumer group")
    void subscriptions() {
        Map<String, String> topicToGroup = Arrays.stream(OrderEventListener.class.getDeclaredMethods())
                .map(m -> m.getAnnotation(KafkaListener.class))
                .filter(a -> a != null)
                .collect(Collectors.toMap(a -> a.topics()[0], KafkaListener::groupId));

        assertThat(topicToGroup).containsOnlyKeys(Topics.INVENTORY_RESERVED, Topics.INVENTORY_FAILED,
                Topics.PAYMENT_PROCESSED, Topics.PAYMENT_FAILED, Topics.REFUND_COMPLETED);
        assertThat(topicToGroup.values()).containsOnly(Topics.GROUP_ORDER);
    }

    @Test
    @DisplayName("no listener method takes an Acknowledgment: offsets are committed by the container after success")
    void noManualAcks() {
        for (Method method : OrderEventListener.class.getDeclaredMethods()) {
            assertThat(method.getParameterTypes()).as(method.getName())
                    .noneMatch(t -> t.getName().endsWith("Acknowledgment"));
        }
    }
}
