package com.ecommerce.orderservice;

import com.ecommerce.common.enums.OrderStatus;
import com.ecommerce.orderservice.dto.CreateOrderRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("Order Controller Integration Tests")
class OrderControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        orderRepository.deleteAll();
    }

    @Test
    @DisplayName("Should create an order successfully")
    void testCreateOrderSuccess() throws Exception {
        CreateOrderRequest request = CreateOrderRequest.builder()
            .customerId(1L)
            .productId("PROD-001")
            .quantity(5)
            .build();

        mockMvc.perform(post("/api/orders")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").exists())
            .andExpect(jsonPath("$.customerId").value(1L))
            .andExpect(jsonPath("$.productId").value("PROD-001"))
            .andExpect(jsonPath("$.quantity").value(5))
            .andExpect(jsonPath("$.status").value(OrderStatus.PENDING.toString()))
            .andExpect(jsonPath("$.createdAt").exists())
            .andExpect(jsonPath("$.updatedAt").exists());
    }

    @Test
    @DisplayName("Should fail to create order with null customer ID")
    void testCreateOrderNullCustomerId() throws Exception {
        CreateOrderRequest request = CreateOrderRequest.builder()
            .customerId(null)
            .productId("PROD-001")
            .quantity(5)
            .build();

        mockMvc.perform(post("/api/orders")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"))
            .andExpect(jsonPath("$.errors.customerId").exists());
    }

    @Test
    @DisplayName("Should fail to create order with null product ID")
    void testCreateOrderNullProductId() throws Exception {
        CreateOrderRequest request = CreateOrderRequest.builder()
            .customerId(1L)
            .productId(null)
            .quantity(5)
            .build();

        mockMvc.perform(post("/api/orders")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"))
            .andExpect(jsonPath("$.errors.productId").exists());
    }

    @Test
    @DisplayName("Should fail to create order with non-positive quantity")
    void testCreateOrderInvalidQuantity() throws Exception {
        CreateOrderRequest request = CreateOrderRequest.builder()
            .customerId(1L)
            .productId("PROD-001")
            .quantity(-5)
            .build();

        mockMvc.perform(post("/api/orders")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"))
            .andExpect(jsonPath("$.errors.quantity").exists());
    }

    @Test
    @DisplayName("Should get order by ID")
    void testGetOrderById() throws Exception {
        Order order = Order.builder()
            .customerId(2L)
            .productId("PROD-002")
            .quantity(10)
            .status(OrderStatus.PENDING)
            .build();
        Order savedOrder = orderRepository.save(order);

        mockMvc.perform(get("/api/orders/" + savedOrder.getId())
            .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(savedOrder.getId()))
            .andExpect(jsonPath("$.customerId").value(2L))
            .andExpect(jsonPath("$.productId").value("PROD-002"))
            .andExpect(jsonPath("$.quantity").value(10));
    }

    @Test
    @DisplayName("Should return 404 when order not found")
    void testGetOrderNotFound() throws Exception {
        mockMvc.perform(get("/api/orders/999")
            .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"))
            .andExpect(jsonPath("$.message").value(containsString("Order not found")));
    }

    @Test
    @DisplayName("Should get all orders with pagination")
    void testGetAllOrdersWithPagination() throws Exception {
        for (int i = 1; i <= 5; i++) {
            Order order = Order.builder()
                .customerId((long) i)
                .productId("PROD-" + i)
                .quantity(i * 2)
                .status(OrderStatus.PENDING)
                .build();
            orderRepository.save(order);
        }

        mockMvc.perform(get("/api/orders?page=0&size=2&sortBy=id")
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
    @DisplayName("Should update order status")
    void testUpdateOrderStatus() throws Exception {
        Order order = Order.builder()
            .customerId(3L)
            .productId("PROD-003")
            .quantity(15)
            .status(OrderStatus.PENDING)
            .build();
        Order savedOrder = orderRepository.save(order);

        CreateOrderRequest updateRequest = CreateOrderRequest.builder()
            .customerId(3L)
            .productId("PROD-003-UPDATED")
            .quantity(20)
            .build();

        mockMvc.perform(put("/api/orders/" + savedOrder.getId())
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(updateRequest)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.quantity").value(20))
            .andExpect(jsonPath("$.productId").value("PROD-003-UPDATED"));
    }

    @Test
    @DisplayName("Should cancel order")
    void testCancelOrder() throws Exception {
        Order order = Order.builder()
            .customerId(4L)
            .productId("PROD-004")
            .quantity(8)
            .status(OrderStatus.PENDING)
            .build();
        Order savedOrder = orderRepository.save(order);

        mockMvc.perform(post("/api/orders/" + savedOrder.getId() + "/cancel")
            .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value(OrderStatus.CANCELLED.toString()));
    }

    @Test
    @DisplayName("Should handle pagination navigation")
    void testPaginationNavigation() throws Exception {
        for (int i = 1; i <= 30; i++) {
            Order order = Order.builder()
                .customerId((long) (i % 5) + 1)
                .productId("PROD-" + i)
                .quantity(i)
                .status(OrderStatus.PENDING)
                .build();
            orderRepository.save(order);
        }

        mockMvc.perform(get("/api/orders?page=0&size=10&sortBy=id"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.pageNumber").value(0))
            .andExpect(jsonPath("$.isFirst").value(true))
            .andExpect(jsonPath("$.isLast").value(false))
            .andExpect(jsonPath("$.totalPages").value(3));

        mockMvc.perform(get("/api/orders?page=1&size=10&sortBy=id"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.pageNumber").value(1))
            .andExpect(jsonPath("$.isFirst").value(false))
            .andExpect(jsonPath("$.isLast").value(false));

        mockMvc.perform(get("/api/orders?page=2&size=10&sortBy=id"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.pageNumber").value(2))
            .andExpect(jsonPath("$.isFirst").value(false))
            .andExpect(jsonPath("$.isLast").value(true))
            .andExpect(jsonPath("$.content.length()").value(10));
    }
}
