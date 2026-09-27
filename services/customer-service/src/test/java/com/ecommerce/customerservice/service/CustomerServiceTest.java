package com.ecommerce.customerservice.service;

import com.ecommerce.common.constants.ApiConstants;
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

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("CustomerService Unit Tests")
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
            .createdAt(LocalDateTime.now())
            .updatedAt(LocalDateTime.now())
            .build();

        createRequest = CreateCustomerRequest.builder()
            .name("John Doe")
            .email("john@example.com")
            .build();
    }

    @Test
    @DisplayName("Should create customer successfully")
    void testCreateCustomerSuccess() {
        Timer.Sample sample = mock(Timer.Sample.class);
        when(applicationMetrics.recordCustomerCreationTime()).thenReturn(sample);
        when(customerRepository.save(any(Customer.class))).thenReturn(testCustomer);

        CustomerResponse response = customerService.createCustomer(createRequest);

        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(1L);
        assertThat(response.getName()).isEqualTo("John Doe");
        assertThat(response.getEmail()).isEqualTo("john@example.com");
        verify(customerRepository, times(1)).save(any(Customer.class));
        verify(applicationMetrics, times(1)).recordCustomerCreated();
    }

    @Test
    @DisplayName("Should get customer by ID successfully")
    void testGetCustomerSuccess() {
        when(customerRepository.findById(1L)).thenReturn(Optional.of(testCustomer));
        Timer.Sample sample = mock(Timer.Sample.class);
        when(applicationMetrics.recordCustomerCreationTime()).thenReturn(sample);

        CustomerResponse response = customerService.getCustomer(1L);

        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(1L);
        assertThat(response.getName()).isEqualTo("John Doe");
        verify(customerRepository, times(1)).findById(1L);
    }

    @Test
    @DisplayName("Should throw exception when customer not found")
    void testGetCustomerNotFound() {
        when(customerRepository.findById(999L)).thenReturn(Optional.empty());
        Timer.Sample sample = mock(Timer.Sample.class);
        when(applicationMetrics.recordCustomerCreationTime()).thenReturn(sample);

        assertThatThrownBy(() -> customerService.getCustomer(999L))
            .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("Should get all customers with pagination")
    void testGetAllCustomersSuccess() {
        List<Customer> customers = Arrays.asList(testCustomer);
        Page<Customer> page = new PageImpl<>(customers, mock(Pageable.class), 1);
        when(customerRepository.findAll(any(Pageable.class))).thenReturn(page);

        PagedResponse<CustomerResponse> response = customerService.getAllCustomers(0, 10, "id");

        assertThat(response).isNotNull();
        assertThat(response.getContent()).hasSize(1);
        assertThat(response.getPageNumber()).isEqualTo(0);
        assertThat(response.getTotalElements()).isEqualTo(1);
    }

    @Test
    @DisplayName("Should enforce max page size")
    void testGetAllCustomersMaxPageSize() {
        List<Customer> customers = Arrays.asList(testCustomer);
        Page<Customer> page = new PageImpl<>(customers, mock(Pageable.class), 1);
        when(customerRepository.findAll(any(Pageable.class))).thenReturn(page);

        customerService.getAllCustomers(0, 1000, "id");

        verify(customerRepository, times(1)).findAll(argThat(pageable ->
            pageable.getPageSize() <= ApiConstants.MAX_PAGE_SIZE
        ));
    }

    @Test
    @DisplayName("Should update customer successfully")
    void testUpdateCustomerSuccess() {
        Customer updatedCustomer = Customer.builder()
            .id(1L)
            .name("Jane Doe")
            .email("jane@example.com")
            .createdAt(LocalDateTime.now())
            .updatedAt(LocalDateTime.now())
            .build();

        CreateCustomerRequest updateRequest = CreateCustomerRequest.builder()
            .name("Jane Doe")
            .email("jane@example.com")
            .build();

        Timer.Sample sample = mock(Timer.Sample.class);
        when(applicationMetrics.recordCustomerCreationTime()).thenReturn(sample);
        when(customerRepository.findById(1L)).thenReturn(Optional.of(testCustomer));
        when(customerRepository.save(any(Customer.class))).thenReturn(updatedCustomer);

        CustomerResponse response = customerService.updateCustomer(1L, updateRequest);

        assertThat(response).isNotNull();
        assertThat(response.getName()).isEqualTo("Jane Doe");
        assertThat(response.getEmail()).isEqualTo("jane@example.com");
        verify(customerRepository, times(1)).save(any(Customer.class));
    }

    @Test
    @DisplayName("Should throw exception when updating non-existent customer")
    void testUpdateCustomerNotFound() {
        Timer.Sample sample = mock(Timer.Sample.class);
        when(applicationMetrics.recordCustomerCreationTime()).thenReturn(sample);
        when(customerRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> customerService.updateCustomer(999L, createRequest))
            .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("Should delete customer successfully")
    void testDeleteCustomerSuccess() {
        when(customerRepository.existsById(1L)).thenReturn(true);
        doNothing().when(customerRepository).deleteById(1L);

        customerService.deleteCustomer(1L);

        verify(customerRepository, times(1)).deleteById(1L);
    }

    @Test
    @DisplayName("Should throw exception when deleting non-existent customer")
    void testDeleteCustomerNotFound() {
        when(customerRepository.existsById(999L)).thenReturn(false);

        assertThatThrownBy(() -> customerService.deleteCustomer(999L))
            .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("Should handle empty customer list")
    void testGetAllCustomersEmpty() {
        Page<Customer> emptyPage = new PageImpl<>(Arrays.asList(), mock(Pageable.class), 0);
        when(customerRepository.findAll(any(Pageable.class))).thenReturn(emptyPage);

        PagedResponse<CustomerResponse> response = customerService.getAllCustomers(0, 10, "id");

        assertThat(response.getContent()).isEmpty();
        assertThat(response.getTotalElements()).isEqualTo(0);
    }

    @Test
    @DisplayName("Should handle large page numbers")
    void testGetAllCustomersLargePageNumber() {
        Page<Customer> emptyPage = new PageImpl<>(Arrays.asList(), mock(Pageable.class), 0);
        when(customerRepository.findAll(any(Pageable.class))).thenReturn(emptyPage);

        PagedResponse<CustomerResponse> response = customerService.getAllCustomers(1000, 10, "id");

        assertThat(response.getPageNumber()).isEqualTo(1000);
    }

    @Test
    @DisplayName("Should handle null sort field")
    void testGetAllCustomersNullSortBy() {
        Page<Customer> page = new PageImpl<>(Arrays.asList(testCustomer), mock(Pageable.class), 1);
        when(customerRepository.findAll(any(Pageable.class))).thenReturn(page);

        PagedResponse<CustomerResponse> response = customerService.getAllCustomers(0, 10, null);

        assertThat(response).isNotNull();
        verify(customerRepository, times(1)).findAll(any(Pageable.class));
    }
}
