package com.ecommerce.customerservice;

import com.ecommerce.common.dto.PagedResponse;
import com.ecommerce.common.exception.ResourceNotFoundException;
import com.ecommerce.customerservice.dto.CreateCustomerRequest;
import com.ecommerce.customerservice.dto.CustomerResponse;
import com.ecommerce.customerservice.service.CustomerService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.Arrays;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(CustomerController.class)
@AutoConfigureMockMvc(addFilters = false)
@DisplayName("CustomerController Unit Tests")
class CustomerControllerUnitTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private CustomerService customerService;

    @Autowired
    private ObjectMapper objectMapper;

    private CustomerResponse testResponse;
    private CreateCustomerRequest createRequest;

    @BeforeEach
    void setUp() {
        testResponse = CustomerResponse.builder()
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
    void testCreateCustomerSuccess() throws Exception {
        when(customerService.createCustomer(any(CreateCustomerRequest.class)))
            .thenReturn(testResponse);

        mockMvc.perform(post("/api/customers")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(createRequest)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").value(1L))
            .andExpect(jsonPath("$.name").value("John Doe"))
            .andExpect(jsonPath("$.email").value("john@example.com"));

        verify(customerService, times(1)).createCustomer(any(CreateCustomerRequest.class));
    }

    @Test
    @DisplayName("Should get customer by ID successfully")
    void testGetCustomerByIdSuccess() throws Exception {
        when(customerService.getCustomer(1L)).thenReturn(testResponse);

        mockMvc.perform(get("/api/customers/1")
            .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(1L))
            .andExpect(jsonPath("$.name").value("John Doe"));

        verify(customerService, times(1)).getCustomer(1L);
    }

    @Test
    @DisplayName("Should return 404 when customer not found")
    void testGetCustomerNotFound() throws Exception {
        when(customerService.getCustomer(999L))
            .thenThrow(new ResourceNotFoundException("Customer", 999L));

        mockMvc.perform(get("/api/customers/999")
            .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isNotFound());

        verify(customerService, times(1)).getCustomer(999L);
    }

    @Test
    @DisplayName("Should get all customers with pagination")
    void testGetAllCustomersSuccess() throws Exception {
        PagedResponse<CustomerResponse> pagedResponse = PagedResponse.<CustomerResponse>builder()
            .content(Arrays.asList(testResponse))
            .pageNumber(0)
            .pageSize(20)
            .totalElements(1)
            .totalPages(1)
            .isFirst(true)
            .isLast(true)
            .build();

        when(customerService.getAllCustomers(0, 20, "id")).thenReturn(pagedResponse);

        mockMvc.perform(get("/api/customers?page=0&size=20&sortBy=id")
            .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content", hasSize(1)))
            .andExpect(jsonPath("$.pageNumber").value(0))
            .andExpect(jsonPath("$.totalElements").value(1));

        verify(customerService, times(1)).getAllCustomers(0, 20, "id");
    }

    @Test
    @DisplayName("Should update customer successfully")
    void testUpdateCustomerSuccess() throws Exception {
        CustomerResponse updatedResponse = CustomerResponse.builder()
            .id(1L)
            .name("Jane Doe")
            .email("jane@example.com")
            .build();

        CreateCustomerRequest updateRequest = CreateCustomerRequest.builder()
            .name("Jane Doe")
            .email("jane@example.com")
            .build();

        when(customerService.updateCustomer(eq(1L), any(CreateCustomerRequest.class)))
            .thenReturn(updatedResponse);

        mockMvc.perform(put("/api/customers/1")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(updateRequest)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.name").value("Jane Doe"));

        verify(customerService, times(1)).updateCustomer(eq(1L), any(CreateCustomerRequest.class));
    }

    @Test
    @DisplayName("Should delete customer successfully")
    void testDeleteCustomerSuccess() throws Exception {
        doNothing().when(customerService).deleteCustomer(1L);

        mockMvc.perform(delete("/api/customers/1")
            .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isNoContent());

        verify(customerService, times(1)).deleteCustomer(1L);
    }

    @Test
    @DisplayName("Should return 404 when deleting non-existent customer")
    void testDeleteCustomerNotFound() throws Exception {
        doThrow(new ResourceNotFoundException("Customer", 999L))
            .when(customerService).deleteCustomer(999L);

        mockMvc.perform(delete("/api/customers/999")
            .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Should handle invalid email in create")
    void testCreateCustomerInvalidEmail() throws Exception {
        CreateCustomerRequest invalidRequest = CreateCustomerRequest.builder()
            .name("John Doe")
            .email("invalid-email")
            .build();

        mockMvc.perform(post("/api/customers")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(invalidRequest)))
            .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Should handle missing required fields")
    void testCreateCustomerMissingFields() throws Exception {
        CreateCustomerRequest invalidRequest = CreateCustomerRequest.builder()
            .name("John Doe")
            .build();

        mockMvc.perform(post("/api/customers")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(invalidRequest)))
            .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Should handle default pagination parameters")
    void testGetAllCustomersDefaultParameters() throws Exception {
        PagedResponse<CustomerResponse> pagedResponse = PagedResponse.<CustomerResponse>builder()
            .content(Arrays.asList(testResponse))
            .pageNumber(0)
            .pageSize(20)
            .totalElements(1)
            .totalPages(1)
            .build();

        when(customerService.getAllCustomers(0, 20, "id")).thenReturn(pagedResponse);

        mockMvc.perform(get("/api/customers")
            .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk());

        verify(customerService, times(1)).getAllCustomers(0, 20, "id");
    }

    @Test
    @DisplayName("Should handle custom pagination parameters")
    void testGetAllCustomersCustomParameters() throws Exception {
        PagedResponse<CustomerResponse> pagedResponse = PagedResponse.<CustomerResponse>builder()
            .content(Arrays.asList())
            .pageNumber(2)
            .pageSize(50)
            .totalElements(0)
            .totalPages(0)
            .build();

        when(customerService.getAllCustomers(2, 50, "email")).thenReturn(pagedResponse);

        mockMvc.perform(get("/api/customers?page=2&size=50&sortBy=email")
            .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk());

        verify(customerService, times(1)).getAllCustomers(2, 50, "email");
    }
}
