package com.ecommerce.paymentservice.exception;

import com.ecommerce.common.enums.PaymentStatus;
import com.ecommerce.common.exception.ProblemDetailsAdvice;
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

@DisplayName("Payment service error handling")
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
    @DisplayName("an illegal payment transition is a 409 problem")
    void invalidTransition() throws Exception {
        mvc.perform(get("/refund"))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.errorCode").value("PAYMENT_INVALID_TRANSITION"))
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("PENDING")));
    }

    @RestController
    static class Thrower {
        @GetMapping("/refund")
        String refund() {
            throw new InvalidPaymentTransitionException(3L, PaymentStatus.PENDING, PaymentStatus.REFUNDED);
        }
    }
}
