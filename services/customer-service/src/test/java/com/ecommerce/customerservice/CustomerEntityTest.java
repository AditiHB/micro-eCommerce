package com.ecommerce.customerservice;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Customer entity")
class CustomerEntityTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    private Customer customer(String name, String email) {
        return Customer.builder().name(name).email(email).build();
    }

    @Test
    @DisplayName("a customer with a name and a valid email is valid")
    void valid() {
        assertThat(validator.validate(customer("John Doe", "john@example.com"))).isEmpty();
    }

    @Test
    @DisplayName("a missing or malformed name or email is not")
    void invalid() {
        assertThat(validator.validate(customer("", "john@example.com"))).isNotEmpty();
        assertThat(validator.validate(customer("J", "john@example.com"))).isNotEmpty();
        assertThat(validator.validate(customer("John Doe", "not-an-email"))).isNotEmpty();
        assertThat(validator.validate(customer("John Doe", null))).isNotEmpty();
    }

    @Test
    @DisplayName("customers are the same when their ids are the same")
    void equality() {
        assertThat(Customer.builder().id(5L).name("A").build()).isEqualTo(Customer.builder().id(5L).name("B").build());
    }
}
