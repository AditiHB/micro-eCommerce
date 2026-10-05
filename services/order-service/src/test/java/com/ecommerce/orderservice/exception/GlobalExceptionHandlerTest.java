package com.ecommerce.orderservice.exception;

import com.ecommerce.common.enums.OrderStatus;
import com.ecommerce.common.exception.ProblemDetailsAdvice;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("Order service error handling")
class GlobalExceptionHandlerTest {

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new Thrower()).setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    @Test
    @DisplayName("it is the shared Problem Details advice")
    void isTheSharedAdvice() {
        org.assertj.core.api.Assertions.assertThat(new GlobalExceptionHandler()).isInstanceOf(ProblemDetailsAdvice.class);
    }

    @Test
    @DisplayName("an illegal order transition is a 409 problem that names the statuses")
    void invalidTransitionIs409() throws Exception {
        mvc.perform(get("/transition"))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.errorCode").value("ORDER_INVALID_TRANSITION"))
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("COMPLETED")));
    }

    @RestController
    static class Thrower {
        @GetMapping("/transition")
        String transition() {
            throw new InvalidOrderTransitionException(5L, OrderStatus.CANCELLED, OrderStatus.COMPLETED);
        }
    }
}
