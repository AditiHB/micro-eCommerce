package com.ecommerce.orderservice.service;

import com.ecommerce.common.constants.ApiConstants;
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

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("OrderService Unit Tests")
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
            .customerId("customer-123")
            .productId("product-456")
            .quantity(5)
            .status(OrderStatus.PENDING)
            .createdAt(LocalDateTime.now())
            .updatedAt(LocalDateTime.now())
            .build();

        createRequest = CreateOrderRequest.builder()
            .customerId("customer-123")
            .productId("product-456")
            .quantity(5)
            .build();
    }

    @Test
    @DisplayName("Should create order successfully")
    void testCreateOrderSuccess() {
        when(orderRepository.save(any(Order.class))).thenReturn(testOrder);
        doNothing().when(eventPublisher).publishEvent(any(OrderCreatedEvent.class), anyString());

        OrderResponse response = orderService.createOrder(createRequest);

        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(1L);
        assertThat(response.getCustomerId()).isEqualTo("customer-123");
        assertThat(response.getProductId()).isEqualTo("product-456");
        assertThat(response.getQuantity()).isEqualTo(5);
        assertThat(response.getStatus()).isEqualTo(OrderStatus.PENDING);
        verify(orderRepository, times(1)).save(any(Order.class));
        verify(eventPublisher, times(1)).publishEvent(any(OrderCreatedEvent.class), anyString());
    }

    @Test
    @DisplayName("Should get order by ID successfully")
    void testGetOrderSuccess() {
        when(orderRepository.findById(1L)).thenReturn(Optional.of(testOrder));

        OrderResponse response = orderService.getOrder(1L);

        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(1L);
        assertThat(response.getCustomerId()).isEqualTo("customer-123");
        verify(orderRepository, times(1)).findById(1L);
    }

    @Test
    @DisplayName("Should throw exception when order not found")
    void testGetOrderNotFound() {
        when(orderRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.getOrder(999L))
            .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("Should get all orders with pagination")
    void testGetAllOrdersSuccess() {
        List<Order> orders = Arrays.asList(testOrder);
        Page<Order> page = new PageImpl<>(orders, mock(Pageable.class), 1);
        when(orderRepository.findAll(any(Pageable.class))).thenReturn(page);

        PagedResponse<OrderResponse> response = orderService.getAllOrders(0, 10, "id");

        assertThat(response).isNotNull();
        assertThat(response.getContent()).hasSize(1);
        assertThat(response.getPageNumber()).isEqualTo(0);
        assertThat(response.getTotalElements()).isEqualTo(1);
    }

    @Test
    @DisplayName("Should update order status successfully")
    void testUpdateOrderStatusSuccess() {
        Order updatedOrder = Order.builder()
            .id(1L)
            .customerId("customer-123")
            .productId("product-456")
            .quantity(5)
            .status(OrderStatus.COMPLETED)
            .build();

        when(orderRepository.findById(1L)).thenReturn(Optional.of(testOrder));
        when(orderRepository.save(any(Order.class))).thenReturn(updatedOrder);

        OrderResponse response = orderService.updateOrderStatus(1L, OrderStatus.COMPLETED);

        assertThat(response.getStatus()).isEqualTo(OrderStatus.COMPLETED);
        verify(orderRepository, times(1)).save(any(Order.class));
    }

    @Test
    @DisplayName("Should throw exception when updating non-existent order")
    void testUpdateOrderStatusNotFound() {
        when(orderRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.updateOrderStatus(999L, OrderStatus.COMPLETED))
            .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("Should handle multiple order status transitions")
    void testMultipleOrderStatusTransitions() {
        Order order = testOrder;

        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
        when(orderRepository.save(any(Order.class)))
            .thenReturn(Order.builder().id(1L).status(OrderStatus.PENDING).build())
            .thenReturn(Order.builder().id(1L).status(OrderStatus.CONFIRMED).build())
            .thenReturn(Order.builder().id(1L).status(OrderStatus.COMPLETED).build());

        OrderResponse response1 = orderService.updateOrderStatus(1L, OrderStatus.PENDING);
        OrderResponse response2 = orderService.updateOrderStatus(1L, OrderStatus.CONFIRMED);
        OrderResponse response3 = orderService.updateOrderStatus(1L, OrderStatus.COMPLETED);

        assertThat(response1.getStatus()).isEqualTo(OrderStatus.PENDING);
        assertThat(response2.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
        assertThat(response3.getStatus()).isEqualTo(OrderStatus.COMPLETED);
    }

    @Test
    @DisplayName("Should enforce max page size")
    void testGetAllOrdersMaxPageSize() {
        List<Order> orders = Arrays.asList(testOrder);
        Page<Order> page = new PageImpl<>(orders, mock(Pageable.class), 1);
        when(orderRepository.findAll(any(Pageable.class))).thenReturn(page);

        orderService.getAllOrders(0, 1000, "id");

        verify(orderRepository, times(1)).findAll(argThat(pageable ->
            pageable.getPageSize() <= ApiConstants.MAX_PAGE_SIZE
        ));
    }

    @Test
    @DisplayName("Should handle empty orders list")
    void testGetAllOrdersEmpty() {
        Page<Order> emptyPage = new PageImpl<>(Arrays.asList(), mock(Pageable.class), 0);
        when(orderRepository.findAll(any(Pageable.class))).thenReturn(emptyPage);

        PagedResponse<OrderResponse> response = orderService.getAllOrders(0, 10, "id");

        assertThat(response.getContent()).isEmpty();
        assertThat(response.getTotalElements()).isEqualTo(0);
    }

    @Test
    @DisplayName("Should publish event when order is created")
    void testOrderEventPublished() {
        when(orderRepository.save(any(Order.class))).thenReturn(testOrder);
        doNothing().when(eventPublisher).publishEvent(any(OrderCreatedEvent.class), anyString());

        orderService.createOrder(createRequest);

        verify(eventPublisher, times(1)).publishEvent(
            argThat(event -> event instanceof OrderCreatedEvent),
            eq("order-created")
        );
    }

    @Test
    @DisplayName("Should handle order with zero quantity")
    void testCreateOrderZeroQuantity() {
        CreateOrderRequest zeroRequest = CreateOrderRequest.builder()
            .customerId("customer-123")
            .productId("product-456")
            .quantity(0)
            .build();

        Order zeroOrder = Order.builder()
            .id(1L)
            .quantity(0)
            .status(OrderStatus.PENDING)
            .build();

        when(orderRepository.save(any(Order.class))).thenReturn(zeroOrder);
        doNothing().when(eventPublisher).publishEvent(any(OrderCreatedEvent.class), anyString());

        OrderResponse response = orderService.createOrder(zeroRequest);

        assertThat(response.getQuantity()).isEqualTo(0);
    }

    @Test
    @DisplayName("Should handle large quantity orders")
    void testCreateOrderLargeQuantity() {
        CreateOrderRequest largeRequest = CreateOrderRequest.builder()
            .customerId("customer-123")
            .productId("product-456")
            .quantity(999999)
            .build();

        Order largeOrder = Order.builder()
            .id(1L)
            .quantity(999999)
            .status(OrderStatus.PENDING)
            .build();

        when(orderRepository.save(any(Order.class))).thenReturn(largeOrder);
        doNothing().when(eventPublisher).publishEvent(any(OrderCreatedEvent.class), anyString());

        OrderResponse response = orderService.createOrder(largeRequest);

        assertThat(response.getQuantity()).isEqualTo(999999);
    }
}
