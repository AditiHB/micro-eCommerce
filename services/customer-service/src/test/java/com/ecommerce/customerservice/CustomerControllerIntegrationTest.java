package com.ecommerce.customerservice;

import com.ecommerce.customerservice.dto.CreateCustomerRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import com.ecommerce.common.service.UserService;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("Customer Controller Integration Tests")
class CustomerControllerIntegrationTest {

    // AuthController (in this service's own package) depends on UserService, which lives in
    // the common auth subsystem that is deliberately left out of the component scan (see
    // CommonIntegrationConfig). Mocked here purely so the application context can start.
    @MockBean
    private UserService userService;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        customerRepository.deleteAll();
    }

    @Test
    @DisplayName("Should create a customer successfully")
    void testCreateCustomerSuccess() throws Exception {
        CreateCustomerRequest request = CreateCustomerRequest.builder()
            .name("John Doe")
            .email("john@example.com")
            .build();

        mockMvc.perform(post("/api/customers")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").exists())
            .andExpect(jsonPath("$.name").value("John Doe"))
            .andExpect(jsonPath("$.email").value("john@example.com"))
            .andExpect(jsonPath("$.createdAt").exists())
            .andExpect(jsonPath("$.updatedAt").exists());
    }

    @Test
    @DisplayName("Should fail to create customer with invalid email")
    void testCreateCustomerInvalidEmail() throws Exception {
        CreateCustomerRequest request = CreateCustomerRequest.builder()
            .name("John Doe")
            .email("invalid-email")
            .build();

        mockMvc.perform(post("/api/customers")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"))
            .andExpect(jsonPath("$.errors.email").exists());
    }

    @Test
    @DisplayName("Should get customer by ID")
    void testGetCustomerById() throws Exception {
        Customer customer = Customer.builder()
            .name("Jane Doe")
            .email("jane@example.com")
            .build();
        Customer savedCustomer = customerRepository.save(customer);

        mockMvc.perform(get("/api/customers/" + savedCustomer.getId())
            .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(savedCustomer.getId()))
            .andExpect(jsonPath("$.name").value("Jane Doe"))
            .andExpect(jsonPath("$.email").value("jane@example.com"));
    }

    @Test
    @DisplayName("Should return 404 when customer not found")
    void testGetCustomerNotFound() throws Exception {
        mockMvc.perform(get("/api/customers/999")
            .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"))
            .andExpect(jsonPath("$.message").value(containsString("Customer not found")));
    }

    @Test
    @DisplayName("Should get all customers with pagination")
    void testGetAllCustomersWithPagination() throws Exception {
        // Create test data
        for (int i = 1; i <= 5; i++) {
            Customer customer = Customer.builder()
                .name("Customer " + i)
                .email("customer" + i + "@example.com")
                .build();
            customerRepository.save(customer);
        }

        mockMvc.perform(get("/api/customers?page=0&size=2&sortBy=id")
            .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content").isArray())
            .andExpect(jsonPath("$.content.length()").value(2))
            .andExpect(jsonPath("$.pageNumber").value(0))
            .andExpect(jsonPath("$.pageSize").value(2))
            .andExpect(jsonPath("$.totalElements").value(5))
            .andExpect(jsonPath("$.totalPages").value(3))
            .andExpect(jsonPath("$.isFirst").value(true));
    }

    @Test
    @DisplayName("Should update customer successfully")
    void testUpdateCustomerSuccess() throws Exception {
        Customer customer = Customer.builder()
            .name("Old Name")
            .email("old@example.com")
            .build();
        Customer savedCustomer = customerRepository.save(customer);

        CreateCustomerRequest updateRequest = CreateCustomerRequest.builder()
            .name("New Name")
            .email("new@example.com")
            .build();

        mockMvc.perform(put("/api/customers/" + savedCustomer.getId())
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(updateRequest)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.name").value("New Name"))
            .andExpect(jsonPath("$.email").value("new@example.com"));
    }

    @Test
    @DisplayName("Should delete customer successfully")
    void testDeleteCustomerSuccess() throws Exception {
        Customer customer = Customer.builder()
            .name("To Delete")
            .email("delete@example.com")
            .build();
        Customer savedCustomer = customerRepository.save(customer);

        mockMvc.perform(delete("/api/customers/" + savedCustomer.getId())
            .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isNoContent());

        // Verify it's deleted
        mockMvc.perform(get("/api/customers/" + savedCustomer.getId())
            .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Should fail to create customer with blank name")
    void testCreateCustomerBlankName() throws Exception {
        CreateCustomerRequest request = CreateCustomerRequest.builder()
            .name("")
            .email("test@example.com")
            .build();

        mockMvc.perform(post("/api/customers")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"))
            .andExpect(jsonPath("$.errors.name").exists());
    }

    @Test
    @DisplayName("Should handle multiple page navigation")
    void testPaginationNavigation() throws Exception {
        // Create 30 customers
        for (int i = 1; i <= 30; i++) {
            Customer customer = Customer.builder()
                .name("Customer " + i)
                .email("customer" + i + "@example.com")
                .build();
            customerRepository.save(customer);
        }

        // Test first page
        mockMvc.perform(get("/api/customers?page=0&size=10&sortBy=id"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.pageNumber").value(0))
            .andExpect(jsonPath("$.isFirst").value(true))
            .andExpect(jsonPath("$.isLast").value(false))
            .andExpect(jsonPath("$.totalPages").value(3));

        // Test middle page
        mockMvc.perform(get("/api/customers?page=1&size=10&sortBy=id"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.pageNumber").value(1))
            .andExpect(jsonPath("$.isFirst").value(false))
            .andExpect(jsonPath("$.isLast").value(false));

        // Test last page
        mockMvc.perform(get("/api/customers?page=2&size=10&sortBy=id"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.pageNumber").value(2))
            .andExpect(jsonPath("$.isFirst").value(false))
            .andExpect(jsonPath("$.isLast").value(true))
            .andExpect(jsonPath("$.content.length()").value(10));
    }
}
