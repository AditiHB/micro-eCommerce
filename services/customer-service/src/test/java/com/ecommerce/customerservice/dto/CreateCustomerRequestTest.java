package com.ecommerce.customerservice.dto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

@DisplayName("CreateCustomerRequest DTO Tests")
class CreateCustomerRequestTest {

    @Test
    @DisplayName("Should create request with valid data")
    void testValidRequest() {
        CreateCustomerRequest request = CreateCustomerRequest.builder()
            .name("John Doe")
            .email("john@example.com")
            .build();

        assertThat(request.getName()).isEqualTo("John Doe");
        assertThat(request.getEmail()).isEqualTo("john@example.com");
    }

    @Test
    @DisplayName("Should update name via setter")
    void testSetName() {
        CreateCustomerRequest request = new CreateCustomerRequest();
        request.setName("Jane Doe");

        assertThat(request.getName()).isEqualTo("Jane Doe");
    }

    @Test
    @DisplayName("Should update email via setter")
    void testSetEmail() {
        CreateCustomerRequest request = new CreateCustomerRequest();
        request.setEmail("jane@example.com");

        assertThat(request.getEmail()).isEqualTo("jane@example.com");
    }

    @Test
    @DisplayName("Should support all-args constructor")
    void testAllArgsConstructor() {
        CreateCustomerRequest request = new CreateCustomerRequest("Bob Smith", "bob@example.com");

        assertThat(request.getName()).isEqualTo("Bob Smith");
        assertThat(request.getEmail()).isEqualTo("bob@example.com");
    }

    @Test
    @DisplayName("Should support no-args constructor")
    void testNoArgsConstructor() {
        CreateCustomerRequest request = new CreateCustomerRequest();

        assertThat(request.getName()).isNull();
        assertThat(request.getEmail()).isNull();
    }
}
