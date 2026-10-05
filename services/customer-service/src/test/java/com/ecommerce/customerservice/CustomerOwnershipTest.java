package com.ecommerce.customerservice;

import com.ecommerce.common.exception.SecurityExceptionAdvice;
import com.ecommerce.common.security.CurrentUser;
import com.ecommerce.common.security.SecurityConfig;
import com.ecommerce.customerservice.dto.CustomerResponse;
import com.ecommerce.customerservice.exception.GlobalExceptionHandler;
import com.ecommerce.customerservice.service.CustomerService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * A customer may read their own record and nobody else's; the customer list (names and email
 * addresses of everyone) is back-office only. Runs through the real security filter chain.
 */
@WebMvcTest(CustomerController.class)
@Import({SecurityConfig.class, CurrentUser.class, SecurityExceptionAdvice.class, GlobalExceptionHandler.class})
@TestPropertySource(properties = {
        "ecommerce.security.jwk-set-uri=http://localhost:0/jwks",
        "ecommerce.security.issuer=http://issuer.test/realms/ecommerce",
        "ecommerce.security.audience=ecommerce-api"
})
@DisplayName("Customer ownership (object-level authorization)")
class CustomerOwnershipTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private CustomerService customerService;

    @MockBean
    private JwtDecoder jwtDecoder;

    private static RequestPostProcessor customer(long customerId) {
        return jwt().authorities(new SimpleGrantedAuthority("ROLE_USER"))
                .jwt(j -> j.claim("customer_id", String.valueOf(customerId)));
    }

    private static RequestPostProcessor role(String role) {
        return jwt().authorities(new SimpleGrantedAuthority("ROLE_" + role));
    }

    private static CustomerResponse customerResponse(long id) {
        return CustomerResponse.builder().id(id).name("Customer " + id).email("c" + id + "@example.com").build();
    }

    @Test
    @DisplayName("a customer can read their own record")
    void ownRecord() throws Exception {
        when(customerService.getCustomer(5L)).thenReturn(customerResponse(5));

        mockMvc.perform(get("/api/v1/customers/5").with(customer(5))).andExpect(status().isOk());
    }

    @Test
    @DisplayName("a customer cannot read another customer's record, and the service is never asked")
    void otherRecord() throws Exception {
        mockMvc.perform(get("/api/v1/customers/6").with(customer(5))).andExpect(status().isNotFound());

        verify(customerService, never()).getCustomer(6L);
    }

    @Test
    @DisplayName("a customer cannot list all customers")
    void customerCannotList() throws Exception {
        mockMvc.perform(get("/api/v1/customers").with(customer(5))).andExpect(status().isForbidden());

        verify(customerService, never()).getAllCustomers(anyInt(), anyInt(), anyString());
    }

    @Test
    @DisplayName("back office and the notification service account can read any customer")
    void crossCustomerRoles() throws Exception {
        when(customerService.getCustomer(6L)).thenReturn(customerResponse(6));

        for (String role : new String[] {"ADMIN", "MANAGER", "SERVICE"}) {
            mockMvc.perform(get("/api/v1/customers/6").with(role(role))).andExpect(status().isOk());
        }
    }
}
