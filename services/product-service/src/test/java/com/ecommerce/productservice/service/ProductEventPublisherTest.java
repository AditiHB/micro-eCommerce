package com.ecommerce.productservice.service;

import com.ecommerce.productservice.event.ProductCreatedEvent;
import com.ecommerce.productservice.event.ProductDeletedEvent;
import com.ecommerce.productservice.event.ProductUpdatedEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ProductEventPublisherTest {
    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    @InjectMocks
    private ProductEventPublisher eventPublisher;

    @Test
    void testPublishProductCreatedEvent() {
        ProductCreatedEvent event = ProductCreatedEvent.builder()
                .productId(1L)
                .name("Test Product")
                .sku("SKU-001")
                .price(new BigDecimal("99.99"))
                .category("Electronics")
                .quantityAvailable(50)
                .eventTime(LocalDateTime.now())
                .build();

        eventPublisher.publishProductCreatedEvent(event);

        verify(kafkaTemplate).send("product-events", "1", event);
    }

    @Test
    void testPublishProductUpdatedEvent() {
        ProductUpdatedEvent event = ProductUpdatedEvent.builder()
                .productId(1L)
                .name("Updated Product")
                .price(new BigDecimal("109.99"))
                .category("Electronics")
                .quantityAvailable(45)
                .eventTime(LocalDateTime.now())
                .build();

        eventPublisher.publishProductUpdatedEvent(event);

        verify(kafkaTemplate).send("product-events", "1", event);
    }

    @Test
    void testPublishProductDeletedEvent() {
        ProductDeletedEvent event = ProductDeletedEvent.builder()
                .productId(1L)
                .sku("SKU-001")
                .eventTime(LocalDateTime.now())
                .build();

        eventPublisher.publishProductDeletedEvent(event);

        verify(kafkaTemplate).send("product-events", "1", event);
    }
}
