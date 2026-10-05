package com.ecommerce.customerservice;

import com.ecommerce.common.testsupport.PostgresIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The customer API through the full stack on PostgreSQL (the cache runs through its outage path: no Redis here). */
@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
@PostgresIntegrationTest
@WithMockUser(roles = "ADMIN")
@DisplayName("Customer API (full stack, PostgreSQL)")
class CustomerControllerIntegrationTest {

    private static final String CUSTOMERS = "/api/v1/customers";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private CustomerRepository repository;

    @BeforeEach
    void setUp() {
        repository.deleteAllInBatch();
    }

    private long create(String name, String email) throws Exception {
        String location = mockMvc.perform(post(CUSTOMERS).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + name + "\",\"email\":\"" + email + "\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getHeader("Location");
        return Long.parseLong(location.substring(CUSTOMERS.length() + 1));
    }

    @Test
    @DisplayName("creating returns 201 with Location and the version as ETag")
    void create() throws Exception {
        mockMvc.perform(post(CUSTOMERS).contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"John Doe\",\"email\":\"john@example.com\"}"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", startsWith(CUSTOMERS + "/")))
                .andExpect(header().string("ETag", "\"0\""))
                .andExpect(jsonPath("$.email").value("john@example.com"));
    }

    @Test
    @DisplayName("a duplicate email is a 409 problem; an invalid email is a 400 problem naming the field")
    void errors() throws Exception {
        create("John Doe", "john@example.com");

        mockMvc.perform(post(CUSTOMERS).contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Other One\",\"email\":\"john@example.com\"}"))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.errorCode").value("CUSTOMER_EMAIL_EXISTS"));
        mockMvc.perform(post(CUSTOMERS).contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Other One\",\"email\":\"nope\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.email").exists());
    }

    @Test
    @DisplayName("PUT uses its own update shape: an unknown field such as id is rejected, not silently ignored")
    void updateRejectsUnknownFields() throws Exception {
        long id = create("John Doe", "john@example.com");

        mockMvc.perform(put(CUSTOMERS + "/" + id).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Jane Doe\",\"email\":\"jane@example.com\",\"id\":99}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("MALFORMED_REQUEST"));
    }

    @Test
    @DisplayName("update with the current ETag works and bumps it; the old ETag is then a 412 (lost update prevented)")
    void optimisticConcurrency() throws Exception {
        long id = create("John Doe", "john@example.com");

        mockMvc.perform(put(CUSTOMERS + "/" + id).header("If-Match", "\"0\"").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Johnny Doe\",\"email\":\"john@example.com\"}"))
                .andExpect(status().isOk()).andExpect(header().string("ETag", "\"1\"")).andExpect(jsonPath("$.name").value("Johnny Doe"));
        mockMvc.perform(put(CUSTOMERS + "/" + id).header("If-Match", "\"0\"").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Someone Else\",\"email\":\"john@example.com\"}"))
                .andExpect(status().isPreconditionFailed()).andExpect(jsonPath("$.errorCode").value("PRECONDITION_FAILED"));
        mockMvc.perform(get(CUSTOMERS + "/" + id)).andExpect(jsonPath("$.name").value("Johnny Doe"));
    }

    @Test
    @DisplayName("taking another customer's email on update is a 409")
    void updateToTakenEmail() throws Exception {
        create("John Doe", "john@example.com");
        long jane = create("Jane Doe", "jane@example.com");

        mockMvc.perform(put(CUSTOMERS + "/" + jane).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Jane Doe\",\"email\":\"john@example.com\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("reading, listing, sorting and deleting; unknown ids are 404 problems")
    void readListDelete() throws Exception {
        long id = create("John Doe", "john@example.com");
        create("Jane Doe", "jane@example.com");

        mockMvc.perform(get(CUSTOMERS + "/" + id)).andExpect(status().isOk()).andExpect(header().string("ETag", "\"0\""));
        mockMvc.perform(get(CUSTOMERS).param("sortBy", "email")).andExpect(status().isOk()).andExpect(jsonPath("$.content", hasSize(2)));
        mockMvc.perform(get(CUSTOMERS).param("sortBy", "passwordHash")).andExpect(status().isBadRequest());
        mockMvc.perform(delete(CUSTOMERS + "/" + id)).andExpect(status().isNoContent());
        mockMvc.perform(get(CUSTOMERS + "/" + id)).andExpect(status().isNotFound()).andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));
        mockMvc.perform(delete(CUSTOMERS + "/" + id)).andExpect(status().isNotFound());
    }
}
