package com.ecommerce.customerservice.service;

import com.ecommerce.common.constants.ApiConstants;
import com.ecommerce.common.dto.PagedResponse;
import com.ecommerce.common.exception.ResourceNotFoundException;
import com.ecommerce.customerservice.Customer;
import com.ecommerce.customerservice.CustomerRepository;
import com.ecommerce.customerservice.dto.CreateCustomerRequest;
import com.ecommerce.customerservice.dto.CustomerResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class CustomerService {

    private final CustomerRepository customerRepository;

    /**
     * Creates a new customer.
     *
     * @param request the customer creation request
     * @return the created customer response
     */
    public CustomerResponse createCustomer(CreateCustomerRequest request) {
        log.info("Creating customer with email: {}", request.getEmail());

        Customer customer = Customer.builder()
            .name(request.getName())
            .email(request.getEmail())
            .build();

        Customer savedCustomer = customerRepository.save(customer);
        log.info("Customer created successfully with ID: {}", savedCustomer.getId());

        return mapToResponse(savedCustomer);
    }

    /**
     * Retrieves a customer by ID.
     *
     * @param id the customer ID
     * @return the customer response
     * @throws ResourceNotFoundException if customer not found
     */
    @Transactional(readOnly = true)
    public CustomerResponse getCustomer(Long id) {
        log.info("Fetching customer with ID: {}", id);

        Customer customer = customerRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Customer", id));

        return mapToResponse(customer);
    }

    /**
     * Retrieves all customers with pagination.
     *
     * @param pageNumber the page number (0-indexed)
     * @param pageSize the page size
     * @param sortBy the field to sort by
     * @return paged customer responses
     */
    @Transactional(readOnly = true)
    public PagedResponse<CustomerResponse> getAllCustomers(int pageNumber, int pageSize, String sortBy) {
        log.info("Fetching customers - page: {}, size: {}, sortBy: {}", pageNumber, pageSize, sortBy);

        // Validate pagination parameters
        pageSize = Math.min(pageSize, ApiConstants.MAX_PAGE_SIZE);

        Pageable pageable = PageRequest.of(pageNumber, pageSize, Sort.by(sortBy).ascending());
        Page<Customer> page = customerRepository.findAll(pageable);

        List<CustomerResponse> responses = page.getContent()
            .stream()
            .map(this::mapToResponse)
            .toList();

        return PagedResponse.of(responses, pageNumber, pageSize, page.getTotalElements());
    }

    /**
     * Updates a customer.
     *
     * @param id the customer ID
     * @param request the update request
     * @return the updated customer response
     * @throws ResourceNotFoundException if customer not found
     */
    public CustomerResponse updateCustomer(Long id, CreateCustomerRequest request) {
        log.info("Updating customer with ID: {}", id);

        Customer customer = customerRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Customer", id));

        customer.setName(request.getName());
        customer.setEmail(request.getEmail());

        Customer updatedCustomer = customerRepository.save(customer);
        log.info("Customer updated successfully with ID: {}", id);

        return mapToResponse(updatedCustomer);
    }

    /**
     * Deletes a customer.
     *
     * @param id the customer ID
     * @throws ResourceNotFoundException if customer not found
     */
    public void deleteCustomer(Long id) {
        log.info("Deleting customer with ID: {}", id);

        if (!customerRepository.existsById(id)) {
            throw new ResourceNotFoundException("Customer", id);
        }

        customerRepository.deleteById(id);
        log.info("Customer deleted successfully with ID: {}", id);
    }

    private CustomerResponse mapToResponse(Customer customer) {
        return CustomerResponse.builder()
            .id(customer.getId())
            .name(customer.getName())
            .email(customer.getEmail())
            .createdAt(customer.getCreatedAt())
            .updatedAt(customer.getUpdatedAt())
            .build();
    }
}
