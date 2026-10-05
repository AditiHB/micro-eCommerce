package com.ecommerce.productservice.service;

import com.ecommerce.common.events.EventPublisher;
import com.ecommerce.productservice.entity.Product;
import com.ecommerce.productservice.event.ProductCreatedEvent;
import com.ecommerce.productservice.event.ProductDeletedEvent;
import com.ecommerce.productservice.event.ProductUpdatedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Announces catalogue changes. Events go through the transactional outbox, in the same transaction as the
 * change itself - the previous fire-and-forget {@code kafkaTemplate.send} could lose an event if the broker
 * hiccuped, or announce one for a change that then rolled back. Keyed by product id, so every event of one
 * product is delivered in order.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ProductEventPublisher {

    private final EventPublisher eventPublisher;

    public void created(Product product) {
        eventPublisher.publish(new ProductCreatedEvent(product.getId(), product.getName(), product.getSku(),
                product.getPrice(), product.getCurrency(), product.getCategory()));
    }

    public void updated(Product product) {
        eventPublisher.publish(new ProductUpdatedEvent(product.getId(), product.getName(), product.getSku(),
                product.getPrice(), product.getCurrency(), product.getCategory()));
    }

    public void deleted(Long productId, String sku) {
        eventPublisher.publish(new ProductDeletedEvent(productId, sku));
    }
}
