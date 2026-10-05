package com.ecommerce.orderservice;

import com.ecommerce.common.dto.PagedResponse;
import com.ecommerce.common.enums.OrderStatus;
import com.ecommerce.common.exception.SecurityExceptionAdvice;
import com.ecommerce.common.security.CurrentUser;
import com.ecommerce.common.security.SecurityConfig;
import com.ecommerce.orderservice.dto.OrderResponse;
import com.ecommerce.orderservice.exception.GlobalExceptionHandler;
import com.ecommerce.orderservice.service.OrderPlacementService;
import com.ecommerce.orderservice.service.OrderService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Object-level authorization for orders (OWASP API1), run through the real security filter chain
 * with signed-in callers represented by mock tokens: a customer sees and creates only their own
 * orders; back-office roles see everything. Fixes the original finding that any logged-in user could
 * list every customer's orders.
 */
@WebMvcTest(OrderController.class)
@Import({SecurityConfig.class, CurrentUser.class, SecurityExceptionAdvice.class, GlobalExceptionHandler.class})
@TestPropertySource(properties = {
        "ecommerce.security.jwk-set-uri=http://localhost:0/jwks",
        "ecommerce.security.issuer=http://issuer.test/realms/ecommerce",
        "ecommerce.security.audience=ecommerce-api"
})
@DisplayName("Order ownership (object-level authorization)")
class OrderOwnershipTest {

    private static final String BODY = """
            {"customerId": %d, "items": [{"productId": "SKU-001", "quantity": 1}]}""";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private OrderService orderService;

    @MockBean
    private OrderPlacementService placementService;

    @MockBean
    private JwtDecoder jwtDecoder;

    private static RequestPostProcessor customer(long customerId) {
        return jwt().authorities(new SimpleGrantedAuthority("ROLE_USER")).jwt(j -> j.claim("customer_id", String.valueOf(customerId)));
    }

    private static RequestPostProcessor role(String role) {
        return jwt().authorities(new SimpleGrantedAuthority("ROLE_" + role));
    }

    private static OrderResponse order(long id, long customerId) {
        return OrderResponse.builder().id(id).customerId(customerId).status(OrderStatus.PENDING)
                .currency("USD").totalAmount(java.math.BigDecimal.TEN).version(0L).items(List.of()).build();
    }

    @Test
    @DisplayName("a customer can read their own order")
    void ownOrder() throws Exception {
        when(orderService.getOrder(10L)).thenReturn(order(10, 5));

        mockMvc.perform(get("/api/v1/orders/10").with(customer(5))).andExpect(status().isOk());
    }

    @Test
    @DisplayName("a customer asking for someone else's order gets a 404, not the order and not a 403")
    void otherCustomersOrder() throws Exception {
        when(orderService.getOrder(11L)).thenReturn(order(11, 6));

        mockMvc.perform(get("/api/v1/orders/11").with(customer(5))).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("back office can read any order")
    void backOfficeReadsAny() throws Exception {
        when(orderService.getOrder(11L)).thenReturn(order(11, 6));

        mockMvc.perform(get("/api/v1/orders/11").with(role("MANAGER"))).andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/orders/11").with(role("ADMIN"))).andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/orders/11").with(role("SERVICE"))).andExpect(status().isOk());
    }

    @Test
    @DisplayName("listing orders as a customer returns only their own, never the global list")
    void listIsScopedToCustomer() throws Exception {
        when(orderService.getOrdersByCustomer(eq(5L), anyInt(), anyInt(), anyString()))
                .thenReturn(PagedResponse.of(List.of(order(10, 5)), 0, 20, 1));

        mockMvc.perform(get("/api/v1/orders").with(customer(5))).andExpect(status().isOk());

        verify(orderService).getOrdersByCustomer(eq(5L), anyInt(), anyInt(), anyString());
        verify(orderService, never()).getAllOrders(anyInt(), anyInt(), anyString());
    }

    @Test
    @DisplayName("a customer token that is not bound to a customer record cannot list orders")
    void unboundUserCannotList() throws Exception {
        mockMvc.perform(get("/api/v1/orders").with(role("USER"))).andExpect(status().isForbidden());

        verify(orderService, never()).getAllOrders(anyInt(), anyInt(), anyString());
    }

    @Test
    @DisplayName("listing orders as back office returns the global list")
    void backOfficeListsAll() throws Exception {
        when(orderService.getAllOrders(anyInt(), anyInt(), anyString()))
                .thenReturn(PagedResponse.of(List.of(order(10, 5), order(11, 6)), 0, 20, 2));

        mockMvc.perform(get("/api/v1/orders").with(role("MANAGER"))).andExpect(status().isOk());

        verify(orderService).getAllOrders(anyInt(), anyInt(), anyString());
    }

    @Test
    @DisplayName("a customer can place an order for themselves")
    void createForSelf() throws Exception {
        when(placementService.placeOrder(any(), any())).thenReturn(new OrderService.Placement(order(12, 5), false));

        mockMvc.perform(post("/api/v1/orders").with(customer(5)).contentType(MediaType.APPLICATION_JSON)
                .content(BODY.formatted(5))).andExpect(status().isCreated());
    }

    @Test
    @DisplayName("a customer cannot place an order on behalf of another customer")
    void createForOtherIsForbidden() throws Exception {
        mockMvc.perform(post("/api/v1/orders").with(customer(5)).contentType(MediaType.APPLICATION_JSON)
                .content(BODY.formatted(6))).andExpect(status().isForbidden());

        verify(placementService, never()).placeOrder(any(), any());
    }

    @Test
    @DisplayName("back office can place an order for any customer")
    void backOfficeCreatesForAny() throws Exception {
        when(placementService.placeOrder(any(), any())).thenReturn(new OrderService.Placement(order(13, 6), false));

        mockMvc.perform(post("/api/v1/orders").with(role("ADMIN")).contentType(MediaType.APPLICATION_JSON)
                .content(BODY.formatted(6))).andExpect(status().isCreated());
    }

    @Test
    @DisplayName("a customer cannot change an order's status; that is a back-office action")
    void customerCannotChangeStatus() throws Exception {
        mockMvc.perform(put("/api/v1/orders/10/status").param("status", "COMPLETED").with(customer(5)))
                .andExpect(status().isForbidden());

        verify(orderService, never()).updateOrderStatus(any(), any());
    }

    @Test
    @DisplayName("a customer can cancel their own order")
    void cancelOwnOrder() throws Exception {
        when(orderService.getOrder(10L)).thenReturn(order(10, 5));
        when(orderService.cancelOrder(eq(10L), anyString())).thenReturn(order(10, 5));

        mockMvc.perform(post("/api/v1/orders/10/cancel").with(customer(5))).andExpect(status().isOk());

        verify(orderService).cancelOrder(eq(10L), anyString());
    }

    @Test
    @DisplayName("a customer cannot cancel someone else's order, and cannot even learn it exists")
    void cannotCancelOthersOrder() throws Exception {
        when(orderService.getOrder(11L)).thenReturn(order(11, 6));

        mockMvc.perform(post("/api/v1/orders/11/cancel").with(customer(5))).andExpect(status().isNotFound());

        verify(orderService, never()).cancelOrder(any(), anyString());
    }

    @Test
    @DisplayName("cancelling with a stale If-Match is refused with 412 before anything changes")
    void cancelWithStaleEtag() throws Exception {
        when(orderService.getOrder(10L)).thenReturn(order(10, 5)); // version 0

        mockMvc.perform(post("/api/v1/orders/10/cancel").with(customer(5)).header("If-Match", "\"7\""))
                .andExpect(status().isPreconditionFailed());

        verify(orderService, never()).cancelOrder(any(), anyString());
    }

    @Test
    @DisplayName("reading an order returns its version as the ETag")
    void etagOnRead() throws Exception {
        when(orderService.getOrder(10L)).thenReturn(order(10, 5));

        mockMvc.perform(get("/api/v1/orders/10").with(customer(5)))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header().string("ETag", "\"0\""));
    }
}
