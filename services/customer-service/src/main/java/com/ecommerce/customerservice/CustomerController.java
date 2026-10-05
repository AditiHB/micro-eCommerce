package com.ecommerce.customerservice;

import com.ecommerce.common.constants.ApiConstants;
import com.ecommerce.common.dto.PagedResponse;
import com.ecommerce.common.security.CurrentUser;
import com.ecommerce.common.web.EntityTags;
import com.ecommerce.customerservice.dto.CreateCustomerRequest;
import com.ecommerce.customerservice.dto.UpdateCustomerRequest;
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
import org.springframework.http.HttpHeaders;
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
    private final CurrentUser currentUser;

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
        log.info("GET /customers - page: {}, size: {}, sortBy: {}", page, size, sortBy);
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
        log.info("GET /customers/{} - Retrieving customer", id);
        // A USER may only read the customer record their token is bound to (OWASP API1 / BOLA).
        currentUser.requireAccessToCustomer(id, "Customer", id);
        return withEtag(ResponseEntity.ok(), customerService.getCustomer(id));
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
        log.info("POST /customers - Creating new customer");
        CustomerResponse created = customerService.createCustomer(request);
        return withEtag(ResponseEntity.created(java.net.URI.create(
            ApiConstants.API_PREFIX + ApiConstants.CUSTOMERS_ENDPOINT + "/" + created.getId())), created);
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
        @ApiResponse(responseCode = "409", description = "Email already used by another customer"),
        @ApiResponse(responseCode = "412", description = "If-Match does not match the customer's current version")
    })
    public ResponseEntity<CustomerResponse> update(
            @Parameter(description = "Customer ID", example = "1")
            @PathVariable Long id,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Customer update request", required = true)
            @Valid @RequestBody UpdateCustomerRequest request,
            @Parameter(description = "ETag from the GET; the update is refused (412) if the customer changed since")
            @RequestHeader(name = HttpHeaders.IF_MATCH, required = false) String ifMatch) {
        log.info("PUT /customers/{} - Updating customer", id);
        return withEtag(ResponseEntity.ok(), customerService.updateCustomer(id, request, ifMatch));
    }

    private static ResponseEntity<CustomerResponse> withEtag(ResponseEntity.BodyBuilder builder, CustomerResponse customer) {
        return builder.eTag(EntityTags.of(customer.getVersion())).body(customer);
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
        log.info("DELETE /customers/{} - Deleting customer", id);
        customerService.deleteCustomer(id);
        return ResponseEntity.noContent().build();
    }
}
