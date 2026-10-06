package com.ecommerce.inventoryservice.service;

import com.ecommerce.common.events.DomainEvent;
import com.ecommerce.common.events.EventCatalog;
import com.ecommerce.common.events.EventPublisher;
import com.ecommerce.common.events.Topics;
import com.ecommerce.inventoryservice.Product;
import com.ecommerce.inventoryservice.event.ProductCreatedEvent;
import com.ecommerce.inventoryservice.event.ProductDeletedEvent;
import com.ecommerce.inventoryservice.event.ProductUpdatedEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@DisplayName("ProductEventPublisher")
class ProductEventPublisherTest {

    private final EventPublisher outbox = mock(EventPublisher.class);
    private final ProductEventPublisher publisher = new ProductEventPublisher(outbox);

    private Product product() {
        return Product.builder().id(7L).name("Headphones").price(new BigDecimal("79.99")).currency("EUR")
                .sku("SKU-001").category("Electronics").build();
    }

    private <E extends DomainEvent> E captured(Class<E> type) {
        ArgumentCaptor<DomainEvent> captor = ArgumentCaptor.forClass(DomainEvent.class);
        verify(outbox).publish(captor.capture());
        return type.cast(captor.getValue());
    }

    @Test
    @DisplayName("created: the product's current state, keyed by its id")
    void created() {
        publisher.created(product());

        ProductCreatedEvent event = captured(ProductCreatedEvent.class);
        assertThat(event.getSku()).isEqualTo("SKU-001");
        assertThat(event.getPrice()).isEqualByComparingTo("79.99");
        assertThat(event.getCurrency()).isEqualTo("EUR");
        assertThat(event.getAggregateId()).isEqualTo("7");
        assertThat(event.getEventType()).isEqualTo("product.created");
    }

    @Test
    @DisplayName("updated carries the new state so a consumer needs no call back")
    void updated() {
        publisher.updated(product());

        ProductUpdatedEvent event = captured(ProductUpdatedEvent.class);
        assertThat(event.getName()).isEqualTo("Headphones");
        assertThat(event.getEventType()).isEqualTo("product.updated");
    }

    @Test
    @DisplayName("deleted names the product and its SKU")
    void deleted() {
        publisher.deleted(7L, "SKU-001");

        ProductDeletedEvent event = captured(ProductDeletedEvent.class);
        assertThat(event.getProductId()).isEqualTo(7L);
        assertThat(event.getSku()).isEqualTo("SKU-001");
    }

    @Test
    @DisplayName("all three product events are registered in the event catalog on the product topic")
    void catalogued() {
        assertThat(EventCatalog.topicOf(ProductCreatedEvent.class)).isEqualTo(Topics.PRODUCT_EVENTS);
        assertThat(EventCatalog.classFor("product.created")).contains(ProductCreatedEvent.class);
        assertThat(EventCatalog.classFor("product.updated")).contains(ProductUpdatedEvent.class);
        assertThat(EventCatalog.classFor("product.deleted")).contains(ProductDeletedEvent.class);
    }
}
