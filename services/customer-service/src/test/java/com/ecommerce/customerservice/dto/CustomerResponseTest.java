package com.ecommerce.customerservice.dto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

@DisplayName("CustomerResponse DTO Tests")
class CustomerResponseTest {

    @Test
    @DisplayName("Should create response with valid data")
    void testValidResponse() {
        CustomerResponse response = CustomerResponse.builder()
            .id(1L)
            .name("John Doe")
            .email("john@example.com")
            .build();

        assertThat(response.getId()).isEqualTo(1L);
        assertThat(response.getName()).isEqualTo("John Doe");
        assertThat(response.getEmail()).isEqualTo("john@example.com");
    }

    @Test
    @DisplayName("Should update name via setter")
    void testSetName() {
        CustomerResponse response = new CustomerResponse();
        response.setName("Jane Doe");

        assertThat(response.getName()).isEqualTo("Jane Doe");
    }

    @Test
    @DisplayName("Should update email via setter")
    void testSetEmail() {
        CustomerResponse response = new CustomerResponse();
        response.setEmail("jane@example.com");

        assertThat(response.getEmail()).isEqualTo("jane@example.com");
    }

    @Test
    @DisplayName("Should update ID via setter")
    void testSetId() {
        CustomerResponse response = new CustomerResponse();
        response.setId(5L);

        assertThat(response.getId()).isEqualTo(5L);
    }

    @Test
    @DisplayName("Should support all-args constructor")
    void testAllArgsConstructor() {
        CustomerResponse response = new CustomerResponse(10L, "Bob Smith", "bob@example.com", null, null);

        assertThat(response.getId()).isEqualTo(10L);
        assertThat(response.getName()).isEqualTo("Bob Smith");
        assertThat(response.getEmail()).isEqualTo("bob@example.com");
    }

    @Test
    @DisplayName("Should support no-args constructor")
    void testNoArgsConstructor() {
        CustomerResponse response = new CustomerResponse();

        assertThat(response.getId()).isNull();
        assertThat(response.getName()).isNull();
        assertThat(response.getEmail()).isNull();
    }
}
