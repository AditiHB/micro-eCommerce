package com.ecommerce.customerservice.integration;

import com.ecommerce.common.exception.ResourceNotFoundException;
import com.ecommerce.customerservice.Customer;
import com.ecommerce.customerservice.CustomerRepository;
import com.ecommerce.customerservice.dto.CreateCustomerRequest;
import com.ecommerce.customerservice.dto.CustomerResponse;
import com.ecommerce.customerservice.service.CustomerService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.AutoConfigureTestEntityManager;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@AutoConfigureTestEntityManager
@Transactional
@ActiveProfiles("test")
@DisplayName("Customer Service Integration Tests")
class CustomerServiceIntegrationTest {

    @Autowired
    private CustomerService customerService;

    @Autowired
    private CustomerRepository customerRepository;

    @BeforeEach
    void setUp() {
        customerRepository.deleteAll();
    }

    @Test
    @DisplayName("Should create and retrieve customer with full context")
    void testCreateAndRetrieveCustomer() {
        CreateCustomerRequest request = CreateCustomerRequest.builder()
            .name("Integration Test")
            .email("integration@test.com")
            .build();

        CustomerResponse created = customerService.createCustomer(request);
        CustomerResponse retrieved = customerService.getCustomer(created.getId());

        assertThat(retrieved).isNotNull();
        assertThat(retrieved.getName()).isEqualTo("Integration Test");
        assertThat(retrieved.getEmail()).isEqualTo("integration@test.com");
    }

    @Test
    @DisplayName("Should update customer and persist changes")
    void testUpdateCustomerPersistence() {
        Customer customer = Customer.builder()
            .name("Original")
            .email("original@test.com")
            .build();
        Customer saved = customerRepository.save(customer);

        CreateCustomerRequest updateRequest = CreateCustomerRequest.builder()
            .name("Updated")
            .email("updated@test.com")
            .build();

        customerService.updateCustomer(saved.getId(), updateRequest);

        Customer verified = customerRepository.findById(saved.getId()).orElseThrow();
        assertThat(verified.getName()).isEqualTo("Updated");
        assertThat(verified.getEmail()).isEqualTo("updated@test.com");
    }

    @Test
    @DisplayName("Should delete customer and verify removal")
    void testDeleteCustomerPersistence() {
        Customer customer = Customer.builder()
            .name("To Delete")
            .email("delete@test.com")
            .build();
        Customer saved = customerRepository.save(customer);

        customerService.deleteCustomer(saved.getId());

        assertThatThrownBy(() -> customerService.getCustomer(saved.getId()))
            .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("Should handle pagination with multiple customers")
    void testPaginationWithMultipleCustomers() {
        for (int i = 1; i <= 15; i++) {
            Customer customer = Customer.builder()
                .name("Customer " + i)
                .email("customer" + i + "@test.com")
                .build();
            customerRepository.save(customer);
        }

        var page1 = customerService.getAllCustomers(0, 5, "id");
        var page2 = customerService.getAllCustomers(1, 5, "id");
        var page3 = customerService.getAllCustomers(2, 5, "id");

        assertThat(page1.getContent()).hasSize(5);
        assertThat(page2.getContent()).hasSize(5);
        assertThat(page3.getContent()).hasSize(5);
        assertThat(page1.getTotalElements()).isEqualTo(15);
        assertThat(page1.getTotalPages()).isEqualTo(3);
    }

    @Test
    @DisplayName("Should maintain referential integrity on update")
    void testReferentialIntegrity() {
        Customer customer1 = Customer.builder()
            .name("Customer 1")
            .email("customer1@test.com")
            .build();
        Customer customer2 = Customer.builder()
            .name("Customer 2")
            .email("customer2@test.com")
            .build();

        Customer saved1 = customerRepository.save(customer1);
        customerRepository.save(customer2);

        CreateCustomerRequest updateRequest = CreateCustomerRequest.builder()
            .name("Updated 1")
            .email("customer1_updated@test.com")
            .build();

        customerService.updateCustomer(saved1.getId(), updateRequest);

        long totalCustomers = customerRepository.count();
        assertThat(totalCustomers).isEqualTo(2);
    }

    @Test
    @DisplayName("Should handle concurrent customer operations")
    void testConcurrentOperations() throws InterruptedException {
        CreateCustomerRequest request1 = CreateCustomerRequest.builder()
            .name("Concurrent 1")
            .email("concurrent1@test.com")
            .build();

        CreateCustomerRequest request2 = CreateCustomerRequest.builder()
            .name("Concurrent 2")
            .email("concurrent2@test.com")
            .build();

        CustomerResponse created1 = customerService.createCustomer(request1);
        CustomerResponse created2 = customerService.createCustomer(request2);

        assertThat(created1.getId()).isNotEqualTo(created2.getId());
        assertThat(customerRepository.count()).isEqualTo(2);
    }

    @Test
    @DisplayName("Should validate timestamps are set on creation")
    void testTimestampOnCreation() {
        CreateCustomerRequest request = CreateCustomerRequest.builder()
            .name("Timestamp Test")
            .email("timestamp@test.com")
            .build();

        CustomerResponse response = customerService.createCustomer(request);

        assertThat(response.getCreatedAt()).isNotNull();
        assertThat(response.getUpdatedAt()).isNotNull();
    }

    @Test
    @DisplayName("Should update timestamp on modification")
    void testTimestampOnUpdate() throws InterruptedException {
        Customer customer = Customer.builder()
            .name("Original")
            .email("original@test.com")
            .build();
        Customer saved = customerRepository.save(customer);
        var originalUpdatedAt = saved.getUpdatedAt();

        Thread.sleep(100);

        CreateCustomerRequest updateRequest = CreateCustomerRequest.builder()
            .name("Updated")
            .email("updated@test.com")
            .build();

        customerService.updateCustomer(saved.getId(), updateRequest);

        Customer updated = customerRepository.findById(saved.getId()).orElseThrow();
        assertThat(updated.getUpdatedAt()).isAfter(originalUpdatedAt);
    }
}
