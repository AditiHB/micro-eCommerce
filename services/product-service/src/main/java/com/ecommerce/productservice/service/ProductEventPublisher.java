package com.ecommerce.productservice.service;

import com.ecommerce.productservice.event.ProductCreatedEvent;
import com.ecommerce.productservice.event.ProductDeletedEvent;
import com.ecommerce.productservice.event.ProductUpdatedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProductEventPublisher {
    private final KafkaTemplate<String, Object> kafkaTemplate;

    public void publishProductCreatedEvent(ProductCreatedEvent event) {
        log.info("Publishing ProductCreatedEvent for product: {}", event.getProductId());
        kafkaTemplate.send("product-events", event.getProductId().toString(), event);
    }

    public void publishProductUpdatedEvent(ProductUpdatedEvent event) {
        log.info("Publishing ProductUpdatedEvent for product: {}", event.getProductId());
        kafkaTemplate.send("product-events", event.getProductId().toString(), event);
    }

    public void publishProductDeletedEvent(ProductDeletedEvent event) {
        log.info("Publishing ProductDeletedEvent for product: {}", event.getProductId());
        kafkaTemplate.send("product-events", event.getProductId().toString(), event);
    }
}
