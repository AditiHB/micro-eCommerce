package com.ecommerce.customerservice.service;

import com.ecommerce.common.config.CacheConfig;
import com.ecommerce.common.constants.ApiConstants;
import com.ecommerce.common.dto.PagedResponse;
import com.ecommerce.common.exception.BusinessException;
import com.ecommerce.common.exception.ConflictException;
import com.ecommerce.common.exception.ResourceNotFoundException;
import com.ecommerce.common.metrics.ApplicationMetrics;
import com.ecommerce.common.web.EntityTags;
import com.ecommerce.customerservice.Customer;
import com.ecommerce.customerservice.CustomerRepository;
import com.ecommerce.customerservice.dto.CreateCustomerRequest;
import com.ecommerce.customerservice.dto.CustomerResponse;
import com.ecommerce.customerservice.dto.UpdateCustomerRequest;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

/**
 * Customer profiles. A customer's profile is reference data - read far more than it changes - so a single
 * customer read by id is cached (one entry per customer, evicted by key when that customer changes, and only
 * after the change has committed). Lists are not cached: there is no sensible way to evict them per key, and
 * evicting everything on every write is what made the old cache useless under load.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class CustomerService {

    static final Set<String> SORTABLE_FIELDS = Set.of("id", "name", "email", "createdAt", "updatedAt");

    private final CustomerRepository customerRepository;
    private final ApplicationMetrics applicationMetrics;
    private final CustomerMetricsRecorder metricsRecorder;

    public CustomerResponse createCustomer(CreateCustomerRequest request) {
        log.info("Creating customer");
        Timer.Sample sample = applicationMetrics.recordCustomerCreationTime();
        if (customerRepository.existsByEmail(request.getEmail())) {
            throw new ConflictException("A customer with this email already exists", "CUSTOMER_EMAIL_EXISTS");
        }

        Customer saved = customerRepository.saveAndFlush(Customer.builder()
            .name(request.getName())
            .email(request.getEmail())
            .build());
        metricsRecorder.recordCreated(sample);
        log.info("Customer created with ID: {}", saved.getId());
        return mapToResponse(saved);
    }

    @Transactional(readOnly = true)
    @Cacheable(value = CacheConfig.CUSTOMERS_CACHE, key = "#id")
    public CustomerResponse getCustomer(Long id) {
        log.debug("Reading customer {} from the database", id);
        return mapToResponse(customerRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Customer", id)));
    }

    @Transactional(readOnly = true)
    public PagedResponse<CustomerResponse> getAllCustomers(int pageNumber, int pageSize, String sortBy) {
        if (!SORTABLE_FIELDS.contains(sortBy)) {
            throw new BusinessException("Cannot sort by '" + sortBy + "'. Allowed: " + SORTABLE_FIELDS, "INVALID_SORT_FIELD");
        }
        int size = Math.min(Math.max(pageSize, 1), ApiConstants.MAX_PAGE_SIZE);
        Pageable pageable = PageRequest.of(Math.max(pageNumber, 0), size, Sort.by(sortBy).ascending());
        Page<Customer> page = customerRepository.findAll(pageable);
        List<CustomerResponse> responses = page.getContent().stream().map(this::mapToResponse).toList();
        return PagedResponse.of(responses, pageNumber, size, page.getTotalElements());
    }

    /**
     * Replaces the customer's details. If the caller sends the {@code If-Match} they got when they read the
     * customer, the update only applies if nobody changed it since (412 otherwise); the entity's version is the
     * second line of defence if two updates race.
     */
    @CacheEvict(value = CacheConfig.CUSTOMERS_CACHE, key = "#id")
    public CustomerResponse updateCustomer(Long id, UpdateCustomerRequest request, String ifMatch) {
        log.info("Updating customer {}", id);
        Timer.Sample sample = applicationMetrics.recordCustomerCreationTime();
        Customer customer = customerRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Customer", id));
        EntityTags.verifyIfMatch(ifMatch, customer.getVersion());
        if (customerRepository.existsByEmailAndIdNot(request.getEmail(), id)) {
            throw new ConflictException("A customer with this email already exists", "CUSTOMER_EMAIL_EXISTS");
        }

        customer.setName(request.getName());
        customer.setEmail(request.getEmail());
        Customer updated = customerRepository.saveAndFlush(customer);
        metricsRecorder.recordUpdated(sample);
        return mapToResponse(updated);
    }

    @CacheEvict(value = CacheConfig.CUSTOMERS_CACHE, key = "#id")
    public void deleteCustomer(Long id) {
        log.info("Deleting customer {}", id);
        if (!customerRepository.existsById(id)) {
            throw new ResourceNotFoundException("Customer", id);
        }
        customerRepository.deleteById(id);
    }

    private CustomerResponse mapToResponse(Customer customer) {
        return CustomerResponse.builder()
            .id(customer.getId())
            .name(customer.getName())
            .email(customer.getEmail())
            .version(customer.getVersion())
            .createdAt(customer.getCreatedAt())
            .updatedAt(customer.getUpdatedAt())
            .build();
    }
}
