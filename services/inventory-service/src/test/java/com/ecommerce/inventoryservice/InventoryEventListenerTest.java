package com.ecommerce.inventoryservice;

import com.ecommerce.common.events.OrderCancelledEvent;
import com.ecommerce.common.events.OrderCreatedEvent;
import com.ecommerce.common.events.Topics;
import com.ecommerce.common.testsupport.EventSamples;
import com.ecommerce.inventoryservice.service.InventorySagaHandler;
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

@DisplayName("InventoryEventListener")
class InventoryEventListenerTest {

    private final InventorySagaHandler handler = mock(InventorySagaHandler.class);
    private final InventoryEventListener listener = new InventoryEventListener(handler);

    @Test
    @DisplayName("each saga event is handed to the transactional handler")
    void delegates() {
        OrderCreatedEvent created = EventSamples.orderCreated();
        OrderCancelledEvent cancelled = EventSamples.orderCancelled();

        listener.handleOrderCreated(created);
        listener.handleOrderCancelled(cancelled);

        verify(handler).onOrderCreated(created);
        verify(handler).onOrderCancelled(cancelled);
    }

    @Test
    @DisplayName("a failing handler is NOT swallowed: the exception reaches the container so it can retry and dead-letter")
    void failuresPropagate() {
        OrderCreatedEvent created = EventSamples.orderCreated();
        doThrow(new IllegalStateException("deadlock detected")).when(handler).onOrderCreated(created);

        assertThatThrownBy(() -> listener.handleOrderCreated(created)).hasMessage("deadlock detected");
    }

    @Test
    @DisplayName("it reserves on order.created and compensates on order.cancelled - the single compensation trigger")
    void subscriptions() {
        Map<String, String> topicToGroup = Arrays.stream(InventoryEventListener.class.getDeclaredMethods())
                .map(m -> m.getAnnotation(KafkaListener.class))
                .filter(a -> a != null)
                .collect(Collectors.toMap(a -> a.topics()[0], KafkaListener::groupId));

        assertThat(topicToGroup).containsOnlyKeys(Topics.ORDER_CREATED, Topics.ORDER_CANCELLED);
        assertThat(topicToGroup.values()).containsOnly(Topics.GROUP_INVENTORY);
    }
}
