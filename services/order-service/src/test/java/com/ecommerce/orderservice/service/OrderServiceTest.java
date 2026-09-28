package com.ecommerce.orderservice.service;

import com.ecommerce.common.constants.ApiConstants;
import com.ecommerce.common.config.CacheConfig;
import com.ecommerce.common.dto.PagedResponse;
import com.ecommerce.common.enums.OrderStatus;
import com.ecommerce.common.events.EventPublisher;
import com.ecommerce.common.events.OrderCreatedEvent;
import com.ecommerce.common.exception.ResourceNotFoundException;
import com.ecommerce.common.eventsourcing.EventSourcingService;
import com.ecommerce.orderservice.Order;
import com.ecommerce.orderservice.OrderRepository;
import com.ecommerce.orderservice.dto.CreateOrderRequest;
import com.ecommerce.orderservice.dto.OrderResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Order Service Unit Tests")
class OrderServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private EventPublisher eventPublisher;

    @Mock
    private EventSourcingService eventSourcingService;

    @InjectMocks
    private OrderService orderService;

    private Order testOrder;
    private CreateOrderRequest createRequest;

    @BeforeEach
    void setUp() {
        testOrder = Order.builder()
            .id(1L)
            .customerId(1L)
            .productId("PROD-001")
            .quantity(5)
            .status(OrderStatus.PENDING)
            .build();

        createRequest = CreateOrderRequest.builder()
            .customerId(1L)
            .productId("PROD-001")
            .quantity(5)
            .build();
    }

    @Test
    @DisplayName("Should create order successfully")
    void testCreateOrder() {
        when(orderRepository.save(any(Order.class))).thenReturn(testOrder);

        OrderResponse response = orderService.createOrder(createRequest);

        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(1L);
        assertThat(response.getCustomerId()).isEqualTo(1L);
        assertThat(response.getProductId()).isEqualTo("PROD-001");
        assertThat(response.getQuantity()).isEqualTo(5);
        assertThat(response.getStatus()).isEqualTo(OrderStatus.PENDING);
        verify(orderRepository, times(1)).save(any(Order.class));
        verify(eventPublisher, times(1)).publishEvent(any(OrderCreatedEvent.class), eq("order-created"));
    }

    @Test
    @DisplayName("Should retrieve order by ID successfully")
    void testGetOrder() {
        when(orderRepository.findById(1L)).thenReturn(Optional.of(testOrder));

        OrderResponse response = orderService.getOrder(1L);

        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(1L);
        assertThat(response.getCustomerId()).isEqualTo(1L);
        verify(orderRepository, times(1)).findById(1L);
    }

    @Test
    @DisplayName("Should throw ResourceNotFoundException when order not found")
    void testGetOrderNotFound() {
        when(orderRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.getOrder(999L))
            .isInstanceOf(ResourceNotFoundException.class);

        verify(orderRepository, times(1)).findById(999L);
    }

    @Test
    @DisplayName("Should retrieve all orders with pagination")
    void testGetAllOrders() {
        Order order2 = Order.builder()
            .id(2L)
            .customerId(2L)
            .productId("PROD-002")
            .quantity(3)
            .status(OrderStatus.INVENTORY_RESERVED)
            .build();

        List<Order> orders = List.of(testOrder, order2);
        Page<Order> page = new PageImpl<>(orders);

        when(orderRepository.findAll(any(Pageable.class))).thenReturn(page);

        PagedResponse<OrderResponse> response = orderService.getAllOrders(0, 10, "id");

        assertThat(response).isNotNull();
        assertThat(response.getContent()).hasSize(2);
        assertThat(response.getPageNumber()).isEqualTo(0);
        assertThat(response.getPageSize()).isEqualTo(10);
        assertThat(response.getTotalElements()).isEqualTo(2);
        verify(orderRepository, times(1)).findAll(any(Pageable.class));
    }

    @Test
    @DisplayName("Should limit page size to maximum")
    void testGetAllOrdersPageSizeLimit() {
        int largePageSize = ApiConstants.MAX_PAGE_SIZE + 100;
        Page<Order> page = new PageImpl<>(List.of(testOrder));

        when(orderRepository.findAll(any(Pageable.class))).thenReturn(page);

        orderService.getAllOrders(0, largePageSize, "id");

        verify(orderRepository, times(1)).findAll(any(Pageable.class));
    }

    @Test
    @DisplayName("Should update order status successfully")
    void testUpdateOrderStatus() {
        Order updatedOrder = Order.builder()
            .id(1L)
            .customerId(1L)
            .productId("PROD-001")
            .quantity(5)
            .status(OrderStatus.COMPLETED)
            .build();

        when(orderRepository.findById(1L)).thenReturn(Optional.of(testOrder));
        when(orderRepository.save(any(Order.class))).thenReturn(updatedOrder);

        OrderResponse response = orderService.updateOrderStatus(1L, OrderStatus.COMPLETED);

        assertThat(response.getStatus()).isEqualTo(OrderStatus.COMPLETED);
        verify(orderRepository, times(1)).findById(1L);
        verify(orderRepository, times(1)).save(any(Order.class));
    }

    @Test
    @DisplayName("Should throw exception when updating non-existent order")
    void testUpdateOrderStatusNotFound() {
        when(orderRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.updateOrderStatus(999L, OrderStatus.COMPLETED))
            .isInstanceOf(ResourceNotFoundException.class);

        verify(orderRepository, times(1)).findById(999L);
        verify(orderRepository, never()).save(any(Order.class));
    }

    @Test
    @DisplayName("Should properly map order to response")
    void testMapToResponse() {
        when(orderRepository.findById(1L)).thenReturn(Optional.of(testOrder));

        OrderResponse response = orderService.getOrder(1L);

        assertThat(response.getId()).isEqualTo(testOrder.getId());
        assertThat(response.getCustomerId()).isEqualTo(testOrder.getCustomerId());
        assertThat(response.getProductId()).isEqualTo(testOrder.getProductId());
        assertThat(response.getQuantity()).isEqualTo(testOrder.getQuantity());
        assertThat(response.getStatus()).isEqualTo(testOrder.getStatus());
    }

    @Test
    @DisplayName("Should handle empty order list")
    void testGetAllOrdersEmpty() {
        Page<Order> emptyPage = new PageImpl<>(List.of());
        when(orderRepository.findAll(any(Pageable.class))).thenReturn(emptyPage);

        PagedResponse<OrderResponse> response = orderService.getAllOrders(0, 10, "id");

        assertThat(response.getContent()).isEmpty();
        assertThat(response.getTotalElements()).isEqualTo(0);
    }

    @Test
    @DisplayName("Should publish order created event on order creation")
    void testOrderCreatedEventPublished() {
        when(orderRepository.save(any(Order.class))).thenReturn(testOrder);

        orderService.createOrder(createRequest);

        verify(eventPublisher, times(1)).publishEvent(any(OrderCreatedEvent.class), eq("order-created"));
    }

    @Test
    @DisplayName("Should handle order status transitions")
    void testOrderStatusTransitions() {
        Order order = testOrder;
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
        when(orderRepository.save(any(Order.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        orderService.updateOrderStatus(1L, OrderStatus.INVENTORY_RESERVED);
        orderService.updateOrderStatus(1L, OrderStatus.COMPLETED);

        verify(orderRepository, times(2)).save(any(Order.class));
    }
}
