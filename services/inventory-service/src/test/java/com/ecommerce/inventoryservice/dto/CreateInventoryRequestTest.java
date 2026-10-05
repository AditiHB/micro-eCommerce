package com.ecommerce.inventoryservice.dto;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("CreateInventoryRequest")
class CreateInventoryRequestTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    private CreateInventoryRequest request(String productId, Integer quantity) {
        return CreateInventoryRequest.builder().productId(productId).quantity(quantity).build();
    }

    @Test
    @DisplayName("a product with stock - or with none yet - is valid")
    void valid() {
        assertThat(validator.validate(request("SKU-001", 10))).isEmpty();
        assertThat(validator.validate(request("SKU-001", 0))).isEmpty();
    }

    @Test
    @DisplayName("a negative quantity, a missing quantity and a blank product are not")
    void invalid() {
        assertThat(validator.validate(request("SKU-001", -1))).isNotEmpty();
        assertThat(validator.validate(request("SKU-001", null))).isNotEmpty();
        assertThat(validator.validate(request(" ", 1))).isNotEmpty();
    }
}
