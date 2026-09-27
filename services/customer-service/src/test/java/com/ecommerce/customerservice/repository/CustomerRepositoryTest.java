package com.ecommerce.customerservice.repository;

import com.ecommerce.customerservice.Customer;
import com.ecommerce.customerservice.CustomerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.*;

@DataJpaTest
@ActiveProfiles("test")
@DisplayName("Customer Repository Unit Tests")
class CustomerRepositoryTest {

    @Autowired
    private CustomerRepository customerRepository;

    @BeforeEach
    void setUp() {
        customerRepository.deleteAll();
    }

    @Test
    @DisplayName("Should save and retrieve customer successfully")
    void testSaveCustomer() {
        Customer customer = Customer.builder()
            .name("John Doe")
            .email("john@example.com")
            .build();

        Customer savedCustomer = customerRepository.save(customer);

        assertThat(savedCustomer).isNotNull();
        assertThat(savedCustomer.getId()).isNotNull();
        assertThat(savedCustomer.getName()).isEqualTo("John Doe");
        assertThat(savedCustomer.getEmail()).isEqualTo("john@example.com");
    }

    @Test
    @DisplayName("Should find customer by ID")
    void testFindCustomerById() {
        Customer customer = Customer.builder()
            .name("Jane Doe")
            .email("jane@example.com")
            .build();
        Customer savedCustomer = customerRepository.save(customer);

        Customer foundCustomer = customerRepository.findById(savedCustomer.getId()).orElse(null);

        assertThat(foundCustomer).isNotNull();
        assertThat(foundCustomer.getId()).isEqualTo(savedCustomer.getId());
        assertThat(foundCustomer.getEmail()).isEqualTo("jane@example.com");
    }

    @Test
    @DisplayName("Should return empty when customer not found")
    void testFindCustomerByIdNotFound() {
        var result = customerRepository.findById(999L);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("Should update customer successfully")
    void testUpdateCustomer() {
        Customer customer = Customer.builder()
            .name("Old Name")
            .email("old@example.com")
            .build();
        Customer savedCustomer = customerRepository.save(customer);

        savedCustomer.setName("New Name");
        savedCustomer.setEmail("new@example.com");
        customerRepository.save(savedCustomer);

        Customer updatedCustomer = customerRepository.findById(savedCustomer.getId()).orElse(null);

        assertThat(updatedCustomer).isNotNull();
        assertThat(updatedCustomer.getName()).isEqualTo("New Name");
        assertThat(updatedCustomer.getEmail()).isEqualTo("new@example.com");
    }

    @Test
    @DisplayName("Should delete customer successfully")
    void testDeleteCustomer() {
        Customer customer = Customer.builder()
            .name("To Delete")
            .email("delete@example.com")
            .build();
        Customer savedCustomer = customerRepository.save(customer);

        customerRepository.deleteById(savedCustomer.getId());

        var result = customerRepository.findById(savedCustomer.getId());

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("Should check if customer exists")
    void testExistsById() {
        Customer customer = Customer.builder()
            .name("Test")
            .email("test@example.com")
            .build();
        Customer savedCustomer = customerRepository.save(customer);

        assertThat(customerRepository.existsById(savedCustomer.getId())).isTrue();
        assertThat(customerRepository.existsById(999L)).isFalse();
    }

    @Test
    @DisplayName("Should count all customers")
    void testCountCustomers() {
        for (int i = 1; i <= 5; i++) {
            Customer customer = Customer.builder()
                .name("Customer " + i)
                .email("customer" + i + "@example.com")
                .build();
            customerRepository.save(customer);
        }

        long count = customerRepository.count();

        assertThat(count).isEqualTo(5);
    }

    @Test
    @DisplayName("Should return all customers")
    void testFindAllCustomers() {
        for (int i = 1; i <= 3; i++) {
            Customer customer = Customer.builder()
                .name("Customer " + i)
                .email("customer" + i + "@example.com")
                .build();
            customerRepository.save(customer);
        }

        var customers = customerRepository.findAll();

        assertThat(customers).hasSize(3);
        assertThat(customers).extracting("name")
            .containsExactlyInAnyOrder("Customer 1", "Customer 2", "Customer 3");
    }

    @Test
    @DisplayName("Should persist timestamps on save")
    void testTimestampPersistence() {
        Customer customer = Customer.builder()
            .name("Timestamp Test")
            .email("timestamp@example.com")
            .build();

        Customer savedCustomer = customerRepository.save(customer);

        assertThat(savedCustomer.getCreatedAt()).isNotNull();
        assertThat(savedCustomer.getUpdatedAt()).isNotNull();
    }
}
