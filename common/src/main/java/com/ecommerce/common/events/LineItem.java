package com.ecommerce.common.events;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * One order line as carried by the saga events: what was bought, how many, and the unit price that was
 * snapshotted from the catalogue when the order was placed. Events carry this so downstream services never
 * have to call back to find out what they are processing.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class LineItem {

    private String productId;
    private int quantity;
    private BigDecimal unitPrice;

    @JsonIgnore
    public BigDecimal getLineTotal() {
        return unitPrice == null ? BigDecimal.ZERO : unitPrice.multiply(BigDecimal.valueOf(quantity));
    }
}
