package com.ecommerce.notificationservice;

import com.ecommerce.common.enums.NotificationStatus;
import com.ecommerce.common.enums.NotificationType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
@Tag("integration")
@DisplayName("Notification Controller Integration Tests")
class NotificationControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private NotificationRepository notificationRepository;

    private Notification savedNotification;

    @BeforeEach
    void setUp() {
        notificationRepository.deleteAll();

        savedNotification = notificationRepository.save(Notification.builder()
            .customerId(10L)
            .orderId(100L)
            .type(NotificationType.ORDER_CREATED)
            .recipient("jane@example.com")
            .subject("Order received")
            .message("We received your order")
            .status(NotificationStatus.SENT)
            .sourceEventId("evt-1")
            .build());
    }

    @Test
    @DisplayName("Should retrieve a notification by id")
    void testGetByIdSuccess() throws Exception {
        mockMvc.perform(get("/api/notifications/{id}", savedNotification.getId()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(savedNotification.getId()))
            .andExpect(jsonPath("$.customerId").value(10))
            .andExpect(jsonPath("$.orderId").value(100))
            .andExpect(jsonPath("$.type").value("ORDER_CREATED"))
            .andExpect(jsonPath("$.recipient").value("jane@example.com"))
            .andExpect(jsonPath("$.status").value("SENT"));
    }

    @Test
    @DisplayName("Should return 404 for an unknown notification id")
    void testGetByIdNotFound() throws Exception {
        mockMvc.perform(get("/api/notifications/{id}", 999999L))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    @DisplayName("Should list notifications for a customer")
    void testGetByCustomer() throws Exception {
        mockMvc.perform(get("/api/notifications/customer/{customerId}", 10L))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content", hasSize(1)))
            .andExpect(jsonPath("$.content[0].customerId").value(10));
    }

    @Test
    @DisplayName("Should list notifications for an order")
    void testGetByOrder() throws Exception {
        mockMvc.perform(get("/api/notifications/order/{orderId}", 100L))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].orderId").value(100));
    }

    @Test
    @DisplayName("Should list all notifications with pagination")
    void testGetAll() throws Exception {
        mockMvc.perform(get("/api/notifications").param("page", "0").param("size", "20"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content", hasSize(1)))
            .andExpect(jsonPath("$.totalElements").value(1));
    }
}
