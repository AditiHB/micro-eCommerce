package com.ecommerce.paymentservice;

import com.ecommerce.common.inbox.ProcessedEventRepository;
import com.ecommerce.common.outbox.OutboxRepository;
import com.ecommerce.common.testsupport.PostgresIntegrationTest;
import com.ecommerce.paymentservice.service.PaymentSagaHandler;
import com.ecommerce.common.events.InventoryReservedEvent;
import com.ecommerce.common.events.LineItem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
@PostgresIntegrationTest
@WithMockUser(roles = "ADMIN")
@DisplayName("Payment API (full stack, PostgreSQL)")
class PaymentControllerIntegrationTest {

    private static final String PAYMENTS = "/api/v1/payments";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private PaymentRepository payments;
    @Autowired
    private OutboxRepository outbox;
    @Autowired
    private ProcessedEventRepository processed;
    @Autowired
    private PaymentSagaHandler saga;

    @BeforeEach
    void setUp() {
        outbox.deleteAll();
        processed.deleteAll();
        payments.deleteAll();
    }

    private long charge(long orderId, String total) {
        InventoryReservedEvent event = new InventoryReservedEvent(orderId, 7L,
                List.of(new LineItem("SKU-001", 1, new BigDecimal(total))), new BigDecimal(total), "USD");
        saga.onInventoryReserved(event);
        return payments.findByOrderId(orderId).orElseThrow().getId();
    }

    @Test
    @DisplayName("there is no endpoint that creates a charge: money is taken only by the order saga")
    void noChargeEndpoint() throws Exception {
        mockMvc.perform(post(PAYMENTS).contentType(MediaType.APPLICATION_JSON).content("{\"orderId\":1,\"amount\":0.01}"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    @DisplayName("a payment is read by id or by order, with its ETag")
    void read() throws Exception {
        long id = charge(1L, "172.97");

        mockMvc.perform(get(PAYMENTS + "/" + id))
                .andExpect(status().isOk()).andExpect(header().exists("ETag"))
                .andExpect(jsonPath("$.status").value("CAPTURED")).andExpect(jsonPath("$.amount").value(172.97))
                .andExpect(jsonPath("$.currency").value("USD")).andExpect(jsonPath("$.orderId").value(1));
        mockMvc.perform(get(PAYMENTS + "/order/1")).andExpect(status().isOk()).andExpect(jsonPath("$.id").value(id));
        mockMvc.perform(get(PAYMENTS + "/order/999")).andExpect(status().isNotFound());
        mockMvc.perform(get(PAYMENTS + "/999999")).andExpect(status().isNotFound()).andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    @DisplayName("listing is paged and sorts only by known fields")
    void list() throws Exception {
        charge(1L, "10.00");
        charge(2L, "20.00");

        mockMvc.perform(get(PAYMENTS).param("sortBy", "amount")).andExpect(status().isOk()).andExpect(jsonPath("$.content", hasSize(2)));
        mockMvc.perform(get(PAYMENTS).param("sortBy", "processorReference")).andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("refunding a captured payment succeeds once; refunding again is a 409 problem")
    void refund() throws Exception {
        long id = charge(1L, "10.00");

        mockMvc.perform(post(PAYMENTS + "/" + id + "/refund")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("REFUNDED"));
        mockMvc.perform(post(PAYMENTS + "/" + id + "/refund"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.errorCode").value("PAYMENT_INVALID_TRANSITION"));
    }

    @Test
    @DisplayName("a declined payment cannot be refunded (409): no money was taken")
    void refundDeclined() throws Exception {
        long id = charge(1L, "25000.00");

        mockMvc.perform(get(PAYMENTS + "/" + id)).andExpect(jsonPath("$.status").value("FAILED"));
        mockMvc.perform(post(PAYMENTS + "/" + id + "/refund")).andExpect(status().isConflict());
    }

    @Test
    @DisplayName("refunding with a stale If-Match is a 412 and nothing is refunded")
    void refundWithStaleEtag() throws Exception {
        long id = charge(1L, "10.00");

        mockMvc.perform(post(PAYMENTS + "/" + id + "/refund").header("If-Match", "\"99\""))
                .andExpect(status().isPreconditionFailed());
        mockMvc.perform(get(PAYMENTS + "/" + id)).andExpect(jsonPath("$.status").value("CAPTURED"));
    }
}
