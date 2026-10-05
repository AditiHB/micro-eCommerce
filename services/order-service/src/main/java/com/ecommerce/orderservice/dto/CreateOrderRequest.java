package com.ecommerce.orderservice.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * An order request: who it is for and what they want. A request never carries a price or a total - those are
 * taken from the catalogue by the server.
 *
 * <p>Send {@code items} (one or more lines). The single {@code productId} + {@code quantity} pair is the
 * original request shape, still accepted as shorthand for a one-line order.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateOrderRequest {

    public static final int MAX_LINES = 20;

    @NotNull(message = "Customer ID cannot be null")
    private Long customerId;

    @Valid
    @Size(max = MAX_LINES, message = "An order can have at most 20 lines")
    private List<Item> items;

    /** Shorthand for a one-line order; use together with {@link #quantity}, instead of {@link #items}. */
    private String productId;

    private Integer quantity;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Item {
        @NotBlank(message = "Product ID cannot be blank")
        private String productId;

        @NotNull(message = "Quantity cannot be null")
        @Positive(message = "Quantity must be positive")
        @Max(value = 1000, message = "Quantity per line cannot exceed 1000")
        private Integer quantity;
    }

    /** Exactly one of the two shapes must be used. */
    @JsonIgnore
    @AssertTrue(message = "Provide either items, or productId and quantity - not both and not neither")
    public boolean isOneShapeUsed() {
        boolean hasItems = items != null && !items.isEmpty();
        boolean hasShorthand = productId != null || quantity != null;
        return hasItems ^ hasShorthand;
    }

    /** The shorthand needs both halves. */
    @JsonIgnore
    @AssertTrue(message = "productId and quantity must be given together")
    public boolean isShorthandComplete() {
        return (productId == null) == (quantity == null);
    }

    @JsonIgnore
    @AssertTrue(message = "Quantity must be positive")
    public boolean isShorthandQuantityPositive() {
        return quantity == null || quantity > 0;
    }

    /** The lines of this request, whichever shape it was sent in. */
    public List<Item> normalizedItems() {
        if (items != null && !items.isEmpty()) {
            return items;
        }
        List<Item> single = new ArrayList<>(1);
        single.add(new Item(productId, quantity));
        return single;
    }
}
