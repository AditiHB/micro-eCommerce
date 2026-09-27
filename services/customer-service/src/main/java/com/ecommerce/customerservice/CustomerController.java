package com.ecommerce.customerservice;

import com.ecommerce.common.constants.ApiConstants;
import com.ecommerce.common.dto.PagedResponse;
import com.ecommerce.customerservice.dto.CreateCustomerRequest;
import com.ecommerce.customerservice.dto.CustomerResponse;
import com.ecommerce.customerservice.service.CustomerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping(ApiConstants.API_PREFIX + ApiConstants.CUSTOMERS_ENDPOINT)
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Customer Management", description = "APIs for managing customers")
public class CustomerController {

    private final CustomerService customerService;

    /**
     * Get all customers with pagination.
     */
    @GetMapping
    @Operation(summary = "Get all customers", description = "Retrieve all customers with pagination support")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Successfully retrieved customers",
            content = @Content(schema = @Schema(implementation = PagedResponse.class))),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<PagedResponse<CustomerResponse>> getAll(
            @Parameter(description = "Page number (0-indexed)", example = "0")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size", example = "20")
            @RequestParam(defaultValue = ApiConstants.DEFAULT_PAGE_SIZE + "") int size,
            @Parameter(description = "Field to sort by", example = "id")
            @RequestParam(defaultValue = "id") String sortBy) {
        log.info("GET /api/customers - Retrieving customers - page: {}, size: {}, sortBy: {}", page, size, sortBy);
        return ResponseEntity.ok(customerService.getAllCustomers(page, size, sortBy));
    }

    /**
     * Get customer by ID.
     */
    @GetMapping("/{id}")
    @Operation(summary = "Get customer by ID", description = "Retrieve a specific customer by their ID")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Customer found",
            content = @Content(schema = @Schema(implementation = CustomerResponse.class))),
        @ApiResponse(responseCode = "404", description = "Customer not found"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<CustomerResponse> getById(
            @Parameter(description = "Customer ID", example = "1")
            @PathVariable Long id) {
        log.info("GET /api/customers/{} - Retrieving customer", id);
        return ResponseEntity.ok(customerService.getCustomer(id));
    }

    /**
     * Create a new customer.
     */
    @PostMapping
    @Operation(summary = "Create a new customer", description = "Create a new customer with provided information")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "201", description = "Customer created successfully",
            content = @Content(schema = @Schema(implementation = CustomerResponse.class))),
        @ApiResponse(responseCode = "400", description = "Invalid input"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<CustomerResponse> create(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Customer creation request", required = true)
            @Valid @RequestBody CreateCustomerRequest request) {
        log.info("POST /api/customers - Creating new customer with email: {}", request.getEmail());
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(customerService.createCustomer(request));
    }

    /**
     * Update customer.
     */
    @PutMapping("/{id}")
    @Operation(summary = "Update customer", description = "Update an existing customer's information")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Customer updated successfully",
            content = @Content(schema = @Schema(implementation = CustomerResponse.class))),
        @ApiResponse(responseCode = "400", description = "Invalid input"),
        @ApiResponse(responseCode = "404", description = "Customer not found"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<CustomerResponse> update(
            @Parameter(description = "Customer ID", example = "1")
            @PathVariable Long id,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Customer update request", required = true)
            @Valid @RequestBody CreateCustomerRequest request) {
        log.info("PUT /api/customers/{} - Updating customer", id);
        return ResponseEntity.ok(customerService.updateCustomer(id, request));
    }

    /**
     * Delete customer.
     */
    @DeleteMapping("/{id}")
    @Operation(summary = "Delete customer", description = "Delete a customer by their ID")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "204", description = "Customer deleted successfully"),
        @ApiResponse(responseCode = "404", description = "Customer not found"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<Void> delete(
            @Parameter(description = "Customer ID", example = "1")
            @PathVariable Long id) {
        log.info("DELETE /api/customers/{} - Deleting customer", id);
        customerService.deleteCustomer(id);
        return ResponseEntity.noContent().build();
    }
}
