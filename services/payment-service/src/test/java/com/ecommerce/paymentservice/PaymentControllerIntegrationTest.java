package com.ecommerce.paymentservice;

import com.ecommerce.common.enums.PaymentStatus;
import com.ecommerce.paymentservice.dto.ProcessPaymentRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.messaging.Message;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.concurrent.CompletableFuture;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
@Tag("integration")
@DisplayName("Payment Controller Integration Tests")
class PaymentControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private KafkaTemplate<String, Object> kafkaTemplate;

    @BeforeEach
    void setUp() {
        paymentRepository.deleteAll();
        // Kafka isn't available in the test sandbox; EventPublisher.publishEvent() calls
        // kafkaTemplate.send(message).whenComplete(...), so the mock must return a completed
        // future rather than null, or the NPE gets wrapped into an EventPublishingException.
        when(kafkaTemplate.send(any(Message.class))).thenReturn(CompletableFuture.completedFuture(null));
    }

    @Test
    @DisplayName("Should process a payment successfully")
    void testProcessPaymentSuccess() throws Exception {
        ProcessPaymentRequest request = ProcessPaymentRequest.builder()
            .orderId(1L)
            .amount(new BigDecimal("99.99"))
            .build();

        mockMvc.perform(post("/api/payments")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").exists())
            .andExpect(jsonPath("$.orderId").value(1L))
            .andExpect(jsonPath("$.amount").value(99.99))
            .andExpect(jsonPath("$.status").value(PaymentStatus.PROCESSED.toString()))
            .andExpect(jsonPath("$.createdAt").exists())
            .andExpect(jsonPath("$.updatedAt").exists());
    }

    @Test
    @DisplayName("Should fail to process payment with null order ID")
    void testProcessPaymentNullOrderId() throws Exception {
        ProcessPaymentRequest request = ProcessPaymentRequest.builder()
            .orderId(null)
            .amount(new BigDecimal("99.99"))
            .build();

        mockMvc.perform(post("/api/payments")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"))
            .andExpect(jsonPath("$.errors.orderId").exists());
    }

    @Test
    @DisplayName("Should fail to process payment with invalid amount")
    void testProcessPaymentInvalidAmount() throws Exception {
        ProcessPaymentRequest request = ProcessPaymentRequest.builder()
            .orderId(1L)
            .amount(new BigDecimal("0.00"))
            .build();

        mockMvc.perform(post("/api/payments")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"))
            .andExpect(jsonPath("$.errors.amount").exists());
    }

    @Test
    @DisplayName("Should get payment by ID")
    void testGetPaymentById() throws Exception {
        Payment payment = Payment.builder()
            .orderId(2L)
            .amount(new BigDecimal("149.99"))
            .status(PaymentStatus.PROCESSED)
            .build();
        Payment savedPayment = paymentRepository.save(payment);

        mockMvc.perform(get("/api/payments/" + savedPayment.getId())
            .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(savedPayment.getId()))
            .andExpect(jsonPath("$.orderId").value(2L))
            .andExpect(jsonPath("$.amount").value(149.99));
    }

    @Test
    @DisplayName("Should return 404 when payment not found")
    void testGetPaymentNotFound() throws Exception {
        mockMvc.perform(get("/api/payments/999")
            .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"))
            .andExpect(jsonPath("$.message").value(containsString("Payment not found")));
    }

    @Test
    @DisplayName("Should get all payments with pagination")
    void testGetAllPaymentsWithPagination() throws Exception {
        for (int i = 1; i <= 5; i++) {
            Payment payment = Payment.builder()
                .orderId((long) i)
                .amount(new BigDecimal(String.format("%d.99", i * 50)))
                .status(PaymentStatus.PROCESSED)
                .build();
            paymentRepository.save(payment);
        }

        mockMvc.perform(get("/api/payments?page=0&size=2&sortBy=id")
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
    @DisplayName("Should refund a payment")
    void testRefundPayment() throws Exception {
        Payment payment = Payment.builder()
            .orderId(3L)
            .amount(new BigDecimal("199.99"))
            .status(PaymentStatus.PROCESSED)
            .build();
        Payment savedPayment = paymentRepository.save(payment);

        mockMvc.perform(post("/api/payments/" + savedPayment.getId() + "/refund")
            .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value(PaymentStatus.REFUNDED.toString()))
            .andExpect(jsonPath("$.id").value(savedPayment.getId()));
    }

    @Test
    @DisplayName("Should handle pagination navigation")
    void testPaginationNavigation() throws Exception {
        for (int i = 1; i <= 30; i++) {
            Payment payment = Payment.builder()
                .orderId((long) i)
                .amount(new BigDecimal(String.format("%d.50", i * 10)))
                .status(PaymentStatus.PROCESSED)
                .build();
            paymentRepository.save(payment);
        }

        mockMvc.perform(get("/api/payments?page=0&size=10&sortBy=id"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.pageNumber").value(0))
            .andExpect(jsonPath("$.isFirst").value(true))
            .andExpect(jsonPath("$.isLast").value(false))
            .andExpect(jsonPath("$.totalPages").value(3));

        mockMvc.perform(get("/api/payments?page=1&size=10&sortBy=id"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.pageNumber").value(1))
            .andExpect(jsonPath("$.isFirst").value(false))
            .andExpect(jsonPath("$.isLast").value(false));

        mockMvc.perform(get("/api/payments?page=2&size=10&sortBy=id"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.pageNumber").value(2))
            .andExpect(jsonPath("$.isFirst").value(false))
            .andExpect(jsonPath("$.isLast").value(true))
            .andExpect(jsonPath("$.content.length()").value(10));
    }
}
