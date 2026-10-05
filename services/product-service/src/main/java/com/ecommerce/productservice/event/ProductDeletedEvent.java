package com.ecommerce.productservice.event;

import com.ecommerce.common.events.DomainEvent;
import com.ecommerce.common.events.EventSchema;
import com.ecommerce.common.events.Topics;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** A product was removed from the catalogue. */
@Getter
@Setter
@NoArgsConstructor
@EventSchema(type = "product.deleted", topic = Topics.PRODUCT_EVENTS)
public class ProductDeletedEvent extends DomainEvent {

    private Long productId;
    private String sku;

    public ProductDeletedEvent(Long productId, String sku) {
        super(String.valueOf(productId), "Product");
        this.productId = productId;
        this.sku = sku;
    }
}
