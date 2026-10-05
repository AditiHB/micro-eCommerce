package com.ecommerce.paymentservice;

import com.ecommerce.common.events.InventoryReservedEvent;
import com.ecommerce.common.events.OrderCancelledEvent;
import com.ecommerce.common.events.Topics;
import com.ecommerce.common.testsupport.EventSamples;
import com.ecommerce.paymentservice.service.PaymentSagaHandler;
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

@DisplayName("PaymentEventListener")
class PaymentEventListenerTest {

    private final PaymentSagaHandler handler = mock(PaymentSagaHandler.class);
    private final PaymentEventListener listener = new PaymentEventListener(handler);

    @Test
    @DisplayName("each saga event is handed to the transactional handler")
    void delegates() {
        InventoryReservedEvent reserved = EventSamples.inventoryReserved();
        OrderCancelledEvent cancelled = EventSamples.orderCancelled();

        listener.handleInventoryReserved(reserved);
        listener.handleOrderCancelled(cancelled);

        verify(handler).onInventoryReserved(reserved);
        verify(handler).onOrderCancelled(cancelled);
    }

    @Test
    @DisplayName("a processor outage is NOT swallowed: it reaches the container, which retries with backoff")
    void failuresPropagate() {
        InventoryReservedEvent reserved = EventSamples.inventoryReserved();
        doThrow(new IllegalStateException("processor timed out")).when(handler).onInventoryReserved(reserved);

        assertThatThrownBy(() -> listener.handleInventoryReserved(reserved)).hasMessage("processor timed out");
    }

    @Test
    @DisplayName("it charges on inventory.reserved and refunds on order.cancelled, in the payment group")
    void subscriptions() {
        Map<String, String> topicToGroup = Arrays.stream(PaymentEventListener.class.getDeclaredMethods())
                .map(m -> m.getAnnotation(KafkaListener.class))
                .filter(a -> a != null)
                .collect(Collectors.toMap(a -> a.topics()[0], KafkaListener::groupId));

        assertThat(topicToGroup).containsOnlyKeys(Topics.INVENTORY_RESERVED, Topics.ORDER_CANCELLED);
        assertThat(topicToGroup.values()).containsOnly(Topics.GROUP_PAYMENT);
    }
}
