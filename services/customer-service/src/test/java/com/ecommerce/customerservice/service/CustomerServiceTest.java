package com.ecommerce.customerservice.service;

import com.ecommerce.common.exception.BusinessException;
import com.ecommerce.common.exception.ConflictException;
import com.ecommerce.common.exception.PreconditionFailedException;
import com.ecommerce.common.exception.ResourceNotFoundException;
import com.ecommerce.common.metrics.ApplicationMetrics;
import com.ecommerce.customerservice.Customer;
import com.ecommerce.customerservice.CustomerRepository;
import com.ecommerce.customerservice.dto.CreateCustomerRequest;
import com.ecommerce.customerservice.dto.CustomerResponse;
import com.ecommerce.customerservice.dto.UpdateCustomerRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("CustomerService")
class CustomerServiceTest {

    @Mock
    private CustomerRepository repository;
    @Mock
    private ApplicationMetrics metrics;

    private CustomerService service;

    @BeforeEach
    void setUp() {
        service = new CustomerService(repository, metrics);
    }

    private Customer stored(long version) {
        return Customer.builder().id(1L).name("John Doe").email("john@example.com").version(version).build();
    }

    @Test
    @DisplayName("creating a customer stores it and returns it with its version")
    void create() {
        when(repository.existsByEmail("john@example.com")).thenReturn(false);
        when(repository.saveAndFlush(any(Customer.class))).thenAnswer(inv -> {
            Customer c = inv.getArgument(0);
            c.setId(1L);
            c.setVersion(0L);
            return c;
        });

        CustomerResponse response = service.createCustomer(CreateCustomerRequest.builder().name("John Doe").email("john@example.com").build());

        assertThat(response.getId()).isEqualTo(1L);
        assertThat(response.getVersion()).isZero();
    }

    @Test
    @DisplayName("an email already in use is a 409, before anything is written")
    void duplicateEmail() {
        when(repository.existsByEmail("john@example.com")).thenReturn(true);

        assertThatThrownBy(() -> service.createCustomer(CreateCustomerRequest.builder().name("X Y").email("john@example.com").build()))
                .isInstanceOf(ConflictException.class)
                .extracting(e -> ((ConflictException) e).getErrorCode()).isEqualTo("CUSTOMER_EMAIL_EXISTS");
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("an update with the current If-Match, or none, is applied")
    void updateApplied() {
        Customer current = stored(3L);
        when(repository.findById(1L)).thenReturn(Optional.of(current));
        when(repository.saveAndFlush(current)).thenReturn(current);
        UpdateCustomerRequest request = UpdateCustomerRequest.builder().name("Jane Doe").email("jane@example.com").build();

        assertThat(service.updateCustomer(1L, request, "\"3\"").getName()).isEqualTo("Jane Doe");
        assertThat(service.updateCustomer(1L, request, null).getEmail()).isEqualTo("jane@example.com");
    }

    @Test
    @DisplayName("an update with a stale If-Match is a 412 and changes nothing: the lost update is prevented")
    void updateStale() {
        Customer current = stored(3L);
        when(repository.findById(1L)).thenReturn(Optional.of(current));

        assertThatThrownBy(() -> service.updateCustomer(1L,
                UpdateCustomerRequest.builder().name("Jane Doe").email("jane@example.com").build(), "\"2\""))
                .isInstanceOf(PreconditionFailedException.class);

        assertThat(current.getName()).isEqualTo("John Doe");
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("an update that takes another customer's email is a 409")
    void updateToTakenEmail() {
        when(repository.findById(1L)).thenReturn(Optional.of(stored(0L)));
        when(repository.existsByEmailAndIdNot("taken@example.com", 1L)).thenReturn(true);

        assertThatThrownBy(() -> service.updateCustomer(1L,
                UpdateCustomerRequest.builder().name("John Doe").email("taken@example.com").build(), null))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    @DisplayName("an unknown customer is a 404 for read, update and delete")
    void unknown() {
        when(repository.findById(9L)).thenReturn(Optional.empty());
        when(repository.existsById(9L)).thenReturn(false);

        assertThatThrownBy(() -> service.getCustomer(9L)).isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> service.updateCustomer(9L,
                UpdateCustomerRequest.builder().name("Jane Doe").email("j@example.com").build(), null)).isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> service.deleteCustomer(9L)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("sorting is limited to known fields")
    void sortAllowList() {
        assertThatThrownBy(() -> service.getAllCustomers(0, 20, "password"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode()).isEqualTo("INVALID_SORT_FIELD");
    }
}
