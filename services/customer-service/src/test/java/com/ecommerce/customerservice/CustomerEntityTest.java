package com.ecommerce.customerservice;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.*;

@DisplayName("Customer Entity Unit Tests")
class CustomerEntityTest {

    @Test
    @DisplayName("Should create customer entity")
    void testCustomerCreation() {
        Customer customer = Customer.builder()
            .id(1L)
            .name("John Doe")
            .email("john@example.com")
            .createdAt(LocalDateTime.now())
            .updatedAt(LocalDateTime.now())
            .build();

        assertThat(customer.getId()).isEqualTo(1L);
        assertThat(customer.getName()).isEqualTo("John Doe");
        assertThat(customer.getEmail()).isEqualTo("john@example.com");
    }

    @Test
    @DisplayName("Should update customer name")
    void testUpdateCustomerName() {
        Customer customer = Customer.builder()
            .name("John Doe")
            .email("john@example.com")
            .build();

        customer.setName("Jane Doe");

        assertThat(customer.getName()).isEqualTo("Jane Doe");
    }

    @Test
    @DisplayName("Should update customer email")
    void testUpdateCustomerEmail() {
        Customer customer = Customer.builder()
            .name("John Doe")
            .email("john@example.com")
            .build();

        customer.setEmail("jane@example.com");

        assertThat(customer.getEmail()).isEqualTo("jane@example.com");
    }

    @Test
    @DisplayName("Should handle empty customer")
    void testEmptyCustomer() {
        Customer customer = new Customer();

        assertThat(customer.getId()).isNull();
        assertThat(customer.getName()).isNull();
        assertThat(customer.getEmail()).isNull();
    }
}
