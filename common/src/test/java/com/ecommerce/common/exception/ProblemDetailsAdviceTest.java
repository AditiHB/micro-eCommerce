package com.ecommerce.common.exception;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Every failure, whatever its cause, must come back as one shape: an RFC 9457 problem document. */
@DisplayName("Problem Details error contract")
class ProblemDetailsAdviceTest {

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mvc = MockMvcBuilders.standaloneSetup(new FailingController())
                .setControllerAdvice(new ServiceAdvice(), new SecurityExceptionAdvice(), new RequestValidationAdvice())
                .setValidator(validator)
                .build();
    }

    @Test
    @DisplayName("a missing resource is a 404 problem with its error code")
    void notFound() throws Exception {
        mvc.perform(get("/t/not-found"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"))
                .andExpect(jsonPath("$.detail").value("Order not found with id: 5"))
                .andExpect(jsonPath("$.type").value("urn:ecommerce:problem:resource-not-found"))
                .andExpect(jsonPath("$.instance").value("/t/not-found"))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    @DisplayName("each business exception reports its own status")
    void businessStatuses() throws Exception {
        mvc.perform(get("/t/business")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.errorCode").value("BUSINESS_ERROR"));
        mvc.perform(get("/t/conflict")).andExpect(status().isConflict()).andExpect(jsonPath("$.errorCode").value("ORDER_INVALID_TRANSITION"));
        mvc.perform(get("/t/precondition")).andExpect(status().isPreconditionFailed()).andExpect(jsonPath("$.errorCode").value("PRECONDITION_FAILED"));
        mvc.perform(get("/t/unprocessable")).andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.errorCode").value("PRODUCT_NOT_FOUND"));
        mvc.perform(get("/t/unavailable")).andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.errorCode").value("DEPENDENCY_UNAVAILABLE"));
    }

    @Test
    @DisplayName("bean validation failures list the offending fields")
    void validationErrors() throws Exception {
        mvc.perform(post("/t/body").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"\",\"quantity\":-1}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors.name").exists())
                .andExpect(jsonPath("$.errors.quantity").value("must be greater than 0"));
    }

    @Test
    @DisplayName("an unreadable body is a 400 problem, not a stack trace")
    void malformedBody() throws Exception {
        mvc.perform(post("/t/body").contentType(MediaType.APPLICATION_JSON).content("{not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("MALFORMED_REQUEST"));
    }

    @Test
    @DisplayName("Spring's own errors (wrong method, bad parameter) use the same shape")
    void springErrors() throws Exception {
        mvc.perform(post("/t/not-found")).andExpect(status().isMethodNotAllowed())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.errorCode").value("METHOD_NOT_ALLOWED"));
        mvc.perform(get("/t/param")).andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.errorCode").value("BAD_REQUEST"));
        mvc.perform(get("/t/param").param("quantity", "abc")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").exists());
    }

    @Test
    @DisplayName("a constraint violation on a request parameter is a 400 with the field named")
    void constraintViolation() throws Exception {
        mvc.perform(get("/t/constraint")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"));
    }

    @Test
    @DisplayName("access denied is a 403 problem")
    void forbidden() throws Exception {
        mvc.perform(get("/t/forbidden")).andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.errorCode").value("ACCESS_DENIED"));
    }

    @Test
    @DisplayName("an optimistic-lock failure is a 409 telling the caller to re-read")
    void concurrentModification() throws Exception {
        mvc.perform(get("/t/lock")).andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("CONCURRENT_MODIFICATION"));
    }

    @Test
    @DisplayName("an unexpected error is a 500 that leaks nothing")
    void unexpected() throws Exception {
        mvc.perform(get("/t/boom")).andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.errorCode").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.detail").value("An unexpected error occurred"))
                .andExpect(jsonPath("$.stackTrace").doesNotExist());
    }

    @RestControllerAdvice
    static class ServiceAdvice extends ProblemDetailsAdvice {
    }

    record Body(@NotBlank String name, @Positive int quantity) {
    }

    @RestController
    static class FailingController {
        @GetMapping("/t/not-found")
        String notFound() {
            throw new ResourceNotFoundException("Order", 5L);
        }

        @GetMapping("/t/business")
        String business() {
            throw new BusinessException("rule broken");
        }

        @GetMapping("/t/conflict")
        String conflict() {
            throw new ConflictException("cannot go from COMPLETED to PENDING", "ORDER_INVALID_TRANSITION");
        }

        @GetMapping("/t/precondition")
        String precondition() {
            throw new PreconditionFailedException("stale");
        }

        @GetMapping("/t/unprocessable")
        String unprocessable() {
            throw new UnprocessableEntityException("no such product", "PRODUCT_NOT_FOUND");
        }

        @GetMapping("/t/unavailable")
        String unavailable() {
            throw new DependencyUnavailableException("catalogue is down");
        }

        @PostMapping("/t/body")
        String body(@Valid @RequestBody Body body) {
            return "ok";
        }

        @GetMapping("/t/param")
        String param(@RequestParam int quantity) {
            return "ok";
        }

        @GetMapping("/t/constraint")
        String constraint() {
            throw new jakarta.validation.ConstraintViolationException("bad", java.util.Set.of());
        }

        @GetMapping("/t/forbidden")
        String forbidden() {
            throw new AccessDeniedException("nope");
        }

        @GetMapping("/t/lock")
        String lock() {
            throw new ObjectOptimisticLockingFailureException("Order", 5L);
        }

        @GetMapping("/t/boom")
        String boom() {
            throw new IllegalStateException("secret internal detail");
        }
    }
}
