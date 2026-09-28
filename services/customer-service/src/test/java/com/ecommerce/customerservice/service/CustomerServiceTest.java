package com.ecommerce.customerservice.service;

import com.ecommerce.common.constants.ApiConstants;
import com.ecommerce.common.config.CacheConfig;
import com.ecommerce.common.dto.PagedResponse;
import com.ecommerce.common.exception.ResourceNotFoundException;
import com.ecommerce.common.metrics.ApplicationMetrics;
import com.ecommerce.customerservice.Customer;
import com.ecommerce.customerservice.CustomerRepository;
import com.ecommerce.customerservice.dto.CreateCustomerRequest;
import com.ecommerce.customerservice.dto.CustomerResponse;
import io.micrometer.core.instrument.Timer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Customer Service Unit Tests")
class CustomerServiceTest {

    @Mock
    private CustomerRepository customerRepository;

    @Mock
    private ApplicationMetrics applicationMetrics;

    @InjectMocks
    private CustomerService customerService;

    private Customer testCustomer;
    private CreateCustomerRequest createRequest;

    @BeforeEach
    void setUp() {
        testCustomer = Customer.builder()
            .id(1L)
            .name("John Doe")
            .email("john@example.com")
            .build();

        createRequest = CreateCustomerRequest.builder()
            .name("John Doe")
            .email("john@example.com")
            .build();

        when(applicationMetrics.recordCustomerCreationTime()).thenReturn(Timer.start());
    }

    @Test
    @DisplayName("Should create customer successfully")
    void testCreateCustomer() {
        when(customerRepository.save(any(Customer.class))).thenReturn(testCustomer);

        CustomerResponse response = customerService.createCustomer(createRequest);

        assertThat(response).isNotNull();
        assertThat(response.getName()).isEqualTo("John Doe");
        assertThat(response.getEmail()).isEqualTo("john@example.com");
        verify(customerRepository, times(1)).save(any(Customer.class));
        verify(applicationMetrics, times(1)).recordCustomerCreated();
    }

    @Test
    @DisplayName("Should retrieve customer by ID successfully")
    void testGetCustomer() {
        when(customerRepository.findById(1L)).thenReturn(Optional.of(testCustomer));

        CustomerResponse response = customerService.getCustomer(1L);

        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(1L);
        assertThat(response.getName()).isEqualTo("John Doe");
        verify(customerRepository, times(1)).findById(1L);
    }

    @Test
    @DisplayName("Should throw ResourceNotFoundException when customer not found")
    void testGetCustomerNotFound() {
        when(customerRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> customerService.getCustomer(999L))
            .isInstanceOf(ResourceNotFoundException.class);

        verify(customerRepository, times(1)).findById(999L);
    }

    @Test
    @DisplayName("Should retrieve all customers with pagination")
    void testGetAllCustomers() {
        Customer customer2 = Customer.builder()
            .id(2L)
            .name("Jane Doe")
            .email("jane@example.com")
            .build();

        List<Customer> customers = List.of(testCustomer, customer2);
        Page<Customer> page = new PageImpl<>(customers);

        when(customerRepository.findAll(any(Pageable.class))).thenReturn(page);

        PagedResponse<CustomerResponse> response = customerService.getAllCustomers(0, 10, "id");

        assertThat(response).isNotNull();
        assertThat(response.getContent()).hasSize(2);
        assertThat(response.getPageNumber()).isEqualTo(0);
        assertThat(response.getPageSize()).isEqualTo(10);
        assertThat(response.getTotalElements()).isEqualTo(2);
        verify(customerRepository, times(1)).findAll(any(Pageable.class));
    }

    @Test
    @DisplayName("Should limit page size to maximum")
    void testGetAllCustomersPageSizeLimit() {
        int largePageSize = ApiConstants.MAX_PAGE_SIZE + 100;
        Page<Customer> page = new PageImpl<>(List.of(testCustomer));

        when(customerRepository.findAll(any(Pageable.class))).thenReturn(page);

        customerService.getAllCustomers(0, largePageSize, "id");

        verify(customerRepository, times(1)).findAll(any(Pageable.class));
    }

    @Test
    @DisplayName("Should update customer successfully")
    void testUpdateCustomer() {
        Customer updatedCustomer = Customer.builder()
            .id(1L)
            .name("Updated Name")
            .email("updated@example.com")
            .build();

        when(customerRepository.findById(1L)).thenReturn(Optional.of(testCustomer));
        when(customerRepository.save(any(Customer.class))).thenReturn(updatedCustomer);

        CreateCustomerRequest updateRequest = CreateCustomerRequest.builder()
            .name("Updated Name")
            .email("updated@example.com")
            .build();

        CustomerResponse response = customerService.updateCustomer(1L, updateRequest);

        assertThat(response.getName()).isEqualTo("Updated Name");
        assertThat(response.getEmail()).isEqualTo("updated@example.com");
        verify(customerRepository, times(1)).findById(1L);
        verify(customerRepository, times(1)).save(any(Customer.class));
    }

    @Test
    @DisplayName("Should throw exception when updating non-existent customer")
    void testUpdateCustomerNotFound() {
        when(customerRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> customerService.updateCustomer(999L, createRequest))
            .isInstanceOf(ResourceNotFoundException.class);

        verify(customerRepository, times(1)).findById(999L);
        verify(customerRepository, never()).save(any(Customer.class));
    }

    @Test
    @DisplayName("Should delete customer successfully")
    void testDeleteCustomer() {
        when(customerRepository.existsById(1L)).thenReturn(true);

        customerService.deleteCustomer(1L);

        verify(customerRepository, times(1)).existsById(1L);
        verify(customerRepository, times(1)).deleteById(1L);
    }

    @Test
    @DisplayName("Should throw exception when deleting non-existent customer")
    void testDeleteCustomerNotFound() {
        when(customerRepository.existsById(999L)).thenReturn(false);

        assertThatThrownBy(() -> customerService.deleteCustomer(999L))
            .isInstanceOf(ResourceNotFoundException.class);

        verify(customerRepository, times(1)).existsById(999L);
        verify(customerRepository, never()).deleteById(any());
    }

    @Test
    @DisplayName("Should properly map customer to response")
    void testMapToResponse() {
        when(customerRepository.findById(1L)).thenReturn(Optional.of(testCustomer));

        CustomerResponse response = customerService.getCustomer(1L);

        assertThat(response.getId()).isEqualTo(testCustomer.getId());
        assertThat(response.getName()).isEqualTo(testCustomer.getName());
        assertThat(response.getEmail()).isEqualTo(testCustomer.getEmail());
    }

    @Test
    @DisplayName("Should handle empty customer list")
    void testGetAllCustomersEmpty() {
        Page<Customer> emptyPage = new PageImpl<>(List.of());
        when(customerRepository.findAll(any(Pageable.class))).thenReturn(emptyPage);

        PagedResponse<CustomerResponse> response = customerService.getAllCustomers(0, 10, "id");

        assertThat(response.getContent()).isEmpty();
        assertThat(response.getTotalElements()).isEqualTo(0);
    }
}
