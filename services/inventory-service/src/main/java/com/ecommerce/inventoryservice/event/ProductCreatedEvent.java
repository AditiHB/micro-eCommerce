package com.ecommerce.inventoryservice.event;

import com.ecommerce.common.events.DomainEvent;
import com.ecommerce.common.events.EventSchema;
import com.ecommerce.common.events.Topics;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/** A product was added to the catalogue. */
@Getter
@Setter
@NoArgsConstructor
@EventSchema(type = "product.created", topic = Topics.PRODUCT_EVENTS)
public class ProductCreatedEvent extends DomainEvent {

    private Long productId;
    private String name;
    private String sku;
    private BigDecimal price;
    private String currency;
    private String category;

    public ProductCreatedEvent(Long productId, String name, String sku, BigDecimal price, String currency, String category) {
        super(String.valueOf(productId), "Product");
        this.productId = productId;
        this.name = name;
        this.sku = sku;
        this.price = price;
        this.currency = currency;
        this.category = category;
    }
}
