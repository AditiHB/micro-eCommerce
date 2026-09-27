package com.ecommerce.orderservice.service;

import com.ecommerce.common.enums.OrderStatus;
import com.ecommerce.common.events.OrderCreatedEvent;
import com.ecommerce.common.exception.EventPublishingException;
import com.ecommerce.common.exception.ResourceNotFoundException;
import com.ecommerce.orderservice.Order;
import com.ecommerce.orderservice.OrderRepository;
import com.ecommerce.orderservice.dto.CreateOrderRequest;
import com.ecommerce.orderservice.dto.OrderResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class OrderService {

    private final OrderRepository orderRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    private static final String KAFKA_TOPIC_ORDER_CREATED = "order-created";

    /**
     * Creates a new order and publishes an event.
     *
     * @param request the order creation request
     * @return the created order response
     * @throws EventPublishingException if event publishing fails
     */
    public OrderResponse createOrder(CreateOrderRequest request) {
        log.info("Creating order for customer: {}", request.getCustomerId());

        Order order = Order.builder()
            .customerId(request.getCustomerId())
            .productId(request.getProductId())
            .quantity(request.getQuantity())
            .status(OrderStatus.PENDING)
            .build();

        Order savedOrder = orderRepository.save(order);
        log.info("Order created successfully with ID: {}", savedOrder.getId());

        publishOrderCreatedEvent(savedOrder);

        return mapToResponse(savedOrder);
    }

    /**
     * Retrieves an order by ID.
     *
     * @param id the order ID
     * @return the order response
     * @throws ResourceNotFoundException if order not found
     */
    @Transactional(readOnly = true)
    public OrderResponse getOrder(Long id) {
        log.info("Fetching order with ID: {}", id);

        Order order = orderRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Order", id));

        return mapToResponse(order);
    }

    /**
     * Retrieves all orders.
     *
     * @return list of order responses
     */
    @Transactional(readOnly = true)
    public List<OrderResponse> getAllOrders() {
        log.info("Fetching all orders");
        return orderRepository.findAll()
            .stream()
            .map(this::mapToResponse)
            .toList();
    }

    /**
     * Updates order status.
     *
     * @param id the order ID
     * @param status the new status
     * @return the updated order response
     * @throws ResourceNotFoundException if order not found
     */
    public OrderResponse updateOrderStatus(Long id, OrderStatus status) {
        log.info("Updating order {} status to {}", id, status);

        Order order = orderRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Order", id));

        order.setStatus(status);
        Order updatedOrder = orderRepository.save(order);

        log.info("Order status updated successfully");
        return mapToResponse(updatedOrder);
    }

    /**
     * Publishes order created event to Kafka.
     *
     * @param order the created order
     * @throws EventPublishingException if publishing fails
     */
    private void publishOrderCreatedEvent(Order order) {
        try {
            OrderCreatedEvent event = new OrderCreatedEvent(
                order.getId(),
                order.getCustomerId(),
                order.getProductId(),
                order.getQuantity()
            );

            Message<OrderCreatedEvent> message = MessageBuilder
                .withPayload(event)
                .setHeader(KafkaHeaders.TOPIC, KAFKA_TOPIC_ORDER_CREATED)
                .build();

            kafkaTemplate.send(message)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish OrderCreatedEvent for order {}", order.getId(), ex);
                        throw new EventPublishingException("Failed to publish order created event", ex);
                    } else {
                        log.info("OrderCreatedEvent published successfully for order {}", order.getId());
                    }
                });
        } catch (Exception e) {
            log.error("Error publishing OrderCreatedEvent", e);
            throw new EventPublishingException("Failed to publish order created event", e);
        }
    }

    private OrderResponse mapToResponse(Order order) {
        return OrderResponse.builder()
            .id(order.getId())
            .customerId(order.getCustomerId())
            .productId(order.getProductId())
            .quantity(order.getQuantity())
            .status(order.getStatus())
            .createdAt(order.getCreatedAt())
            .updatedAt(order.getUpdatedAt())
            .build();
    }
}
