package com.ecommerce.orderservice.dto;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("CreateOrderRequest")
class CreateOrderRequestTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    private static CreateOrderRequest.Item item(String sku, Integer quantity) {
        return new CreateOrderRequest.Item(sku, quantity);
    }

    private Set<ConstraintViolation<CreateOrderRequest>> validate(CreateOrderRequest request) {
        return validator.validate(request);
    }

    @Test
    @DisplayName("a multi-line request is valid")
    void multiLine() {
        CreateOrderRequest request = CreateOrderRequest.builder().customerId(1L)
                .items(List.of(item("SKU-001", 2), item("SKU-002", 1))).build();

        assertThat(validate(request)).isEmpty();
        assertThat(request.normalizedItems()).hasSize(2);
    }

    @Test
    @DisplayName("the original single productId + quantity shape still works, as one line")
    void legacyShorthand() {
        CreateOrderRequest request = CreateOrderRequest.builder().customerId(1L).productId("SKU-001").quantity(3).build();

        assertThat(validate(request)).isEmpty();
        assertThat(request.normalizedItems()).singleElement().satisfies(i -> {
            assertThat(i.getProductId()).isEqualTo("SKU-001");
            assertThat(i.getQuantity()).isEqualTo(3);
        });
    }

    @Test
    @DisplayName("a request must use one shape: items, or the shorthand - not both, not neither")
    void exactlyOneShape() {
        assertThat(validate(CreateOrderRequest.builder().customerId(1L).build())).isNotEmpty();
        assertThat(validate(CreateOrderRequest.builder().customerId(1L).items(List.of(item("SKU-001", 1)))
                .productId("SKU-002").quantity(1).build())).isNotEmpty();
    }

    @Test
    @DisplayName("the shorthand needs both halves")
    void shorthandNeedsBoth() {
        assertThat(validate(CreateOrderRequest.builder().customerId(1L).productId("SKU-001").build())).isNotEmpty();
        assertThat(validate(CreateOrderRequest.builder().customerId(1L).quantity(2).build())).isNotEmpty();
    }

    @Test
    @DisplayName("the customer is required")
    void customerRequired() {
        assertThat(validate(CreateOrderRequest.builder().items(List.of(item("SKU-001", 1))).build()))
                .extracting(v -> v.getPropertyPath().toString()).contains("customerId");
    }

    @Test
    @DisplayName("quantities must be positive and bounded; product ids must not be blank")
    void lineConstraints() {
        assertThat(validate(CreateOrderRequest.builder().customerId(1L).items(List.of(item("SKU-001", 0))).build())).isNotEmpty();
        assertThat(validate(CreateOrderRequest.builder().customerId(1L).items(List.of(item("SKU-001", -5))).build())).isNotEmpty();
        assertThat(validate(CreateOrderRequest.builder().customerId(1L).items(List.of(item("SKU-001", 1001))).build())).isNotEmpty();
        assertThat(validate(CreateOrderRequest.builder().customerId(1L).items(List.of(item(" ", 1))).build())).isNotEmpty();
        assertThat(validate(CreateOrderRequest.builder().customerId(1L).productId("SKU-001").quantity(-1).build())).isNotEmpty();
    }

    @Test
    @DisplayName("an order has a bounded number of lines")
    void maxLines() {
        List<CreateOrderRequest.Item> tooMany = new ArrayList<>();
        for (int i = 0; i <= CreateOrderRequest.MAX_LINES; i++) {
            tooMany.add(item("SKU-" + i, 1));
        }

        assertThat(validate(CreateOrderRequest.builder().customerId(1L).items(tooMany).build())).isNotEmpty();
    }
}
