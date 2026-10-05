package com.ecommerce.inventoryservice;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Inventory entity")
class InventoryEntityTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    private Inventory stock(Integer quantity) {
        return Inventory.builder().productId("SKU-001").quantity(quantity).build();
    }

    @Test
    @DisplayName("stock may be exactly zero - the last unit is sellable and a sold-out item is a real state")
    void zeroIsValid() {
        assertThat(validator.validate(stock(0))).isEmpty();
        assertThat(validator.validate(stock(5))).isEmpty();
    }

    @Test
    @DisplayName("stock can never be negative")
    void negativeIsInvalid() {
        assertThat(validator.validate(stock(-1))).isNotEmpty();
    }

    @Test
    @DisplayName("a product id and a quantity are required")
    void requiredFields() {
        assertThat(validator.validate(stock(null))).isNotEmpty();
        assertThat(validator.validate(Inventory.builder().productId(" ").quantity(1).build())).isNotEmpty();
    }
}
