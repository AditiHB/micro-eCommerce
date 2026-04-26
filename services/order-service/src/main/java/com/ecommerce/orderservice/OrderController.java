package com.ecommerce.orderservice;

import com.ecommerce.common.events.OrderCreatedEvent;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/orders")
public class OrderController {
    
    private final OrderRepository repository;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    public OrderController(OrderRepository repository, KafkaTemplate<String, Object> kafkaTemplate) {
        this.repository = repository;
        this.kafkaTemplate = kafkaTemplate;
    }

    @GetMapping
    public List<Order> getAll() {
        return repository.findAll();
    }

    @PostMapping
    @io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker(name = "orderService", fallbackMethod = "createOrderFallback")
    @io.github.resilience4j.retry.annotation.Retry(name = "orderService")
    public Order createOrder(@RequestBody Order order) {
        order.setStatus("PENDING");
        Order savedOrder = repository.save(order);
        
        OrderCreatedEvent event = new OrderCreatedEvent(
                savedOrder.getId(), savedOrder.getCustomerId(), 
                savedOrder.getProductId(), savedOrder.getQuantity()
        );
        kafkaTemplate.send("order-created", event);
        
        return savedOrder;
    }

    public Order createOrderFallback(Order order, Throwable t) {
        Order fallbackOrder = new Order();
        fallbackOrder.setStatus("FAILED_SYSTEM_UNAVAILABLE");
        return fallbackOrder;
    }
}
