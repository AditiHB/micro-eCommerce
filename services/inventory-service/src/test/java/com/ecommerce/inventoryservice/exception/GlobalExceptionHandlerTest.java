package com.ecommerce.inventoryservice.exception;

import com.ecommerce.common.exception.ConflictException;
import com.ecommerce.common.exception.ProblemDetailsAdvice;
import com.ecommerce.common.exception.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("Inventory service error handling")
class GlobalExceptionHandlerTest {

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new Thrower()).setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    @Test
    @DisplayName("it is the shared Problem Details advice")
    void isTheSharedAdvice() {
        assertThat(new GlobalExceptionHandler()).isInstanceOf(ProblemDetailsAdvice.class);
    }

    @Test
    @DisplayName("insufficient stock is a 409 problem with its own code")
    void insufficientStock() throws Exception {
        mvc.perform(get("/stock"))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.errorCode").value("INSUFFICIENT_STOCK"));
    }

    @Test
    @DisplayName("a missing inventory item is a 404 problem")
    void notFound() throws Exception {
        mvc.perform(get("/missing")).andExpect(status().isNotFound()).andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));
    }

    @RestController
    static class Thrower {
        @GetMapping("/stock")
        String stock() {
            throw new ConflictException("Insufficient stock. Available: 1, Requested: 5", "INSUFFICIENT_STOCK");
        }

        @GetMapping("/missing")
        String missing() {
            throw new ResourceNotFoundException("Inventory", 9L);
        }
    }
}
