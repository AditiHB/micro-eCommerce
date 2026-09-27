package com.ecommerce.customerservice;

import com.ecommerce.customerservice.dto.CreateCustomerRequest;
import com.ecommerce.customerservice.dto.CustomerResponse;
import com.ecommerce.customerservice.service.CustomerService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/customers")
@RequiredArgsConstructor
@Slf4j
public class CustomerController {

    private final CustomerService customerService;

    /**
     * Get all customers.
     */
    @GetMapping
    public ResponseEntity<List<CustomerResponse>> getAll() {
        log.info("GET /api/customers - Retrieving all customers");
        return ResponseEntity.ok(customerService.getAllCustomers());
    }

    /**
     * Get customer by ID.
     */
    @GetMapping("/{id}")
    public ResponseEntity<CustomerResponse> getById(@PathVariable Long id) {
        log.info("GET /api/customers/{} - Retrieving customer", id);
        return ResponseEntity.ok(customerService.getCustomer(id));
    }

    /**
     * Create a new customer.
     */
    @PostMapping
    public ResponseEntity<CustomerResponse> create(@Valid @RequestBody CreateCustomerRequest request) {
        log.info("POST /api/customers - Creating new customer with email: {}", request.getEmail());
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(customerService.createCustomer(request));
    }

    /**
     * Update customer.
     */
    @PutMapping("/{id}")
    public ResponseEntity<CustomerResponse> update(
            @PathVariable Long id,
            @Valid @RequestBody CreateCustomerRequest request) {
        log.info("PUT /api/customers/{} - Updating customer", id);
        return ResponseEntity.ok(customerService.updateCustomer(id, request));
    }

    /**
     * Delete customer.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        log.info("DELETE /api/customers/{} - Deleting customer", id);
        customerService.deleteCustomer(id);
        return ResponseEntity.noContent().build();
    }
}
