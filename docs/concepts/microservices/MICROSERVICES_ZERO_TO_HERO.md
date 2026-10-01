# Microservices Zero to Hero 🚀
## Complete Production Reference Guide

---

## 📚 Table of Contents

1. [Core Concepts Deep Dive](#core-concepts)
2. [Microservices Design Patterns](#design-patterns)
3. [Resilience Patterns](#resilience-patterns)
4. [Data Consistency & Transactions](#data-consistency)
5. [Communication Strategies](#communication)
6. [Monitoring & Observability](#monitoring)
7. [Security Best Practices](#security)
8. [Production Deployment](#deployment)
9. [Real-World Scenarios](#scenarios)
10. [Complete Code Examples](#code)
11. [Troubleshooting Guide](#troubleshooting)

---

## 🏛️ Core Concepts Deep Dive {#core-concepts}

### **Microservices Architecture Principles**

#### **1. Loose Coupling**

```java
// ❌ WRONG: Tightly Coupled Services

// Payment Service directly depends on Order Service
@RestController
@RequestMapping("/payments")
public class PaymentController {
    
    @Autowired
    private OrderServiceClient orderClient;  // ← Direct dependency!
    
    @PostMapping("/{orderId}")
    public PaymentResponse processPayment(@PathVariable Long orderId) {
        // Call Order Service directly (blocking)
        Order order = orderClient.getOrder(orderId);  // ← Tight coupling!
        
        if (order == null) throw new Exception("Order not found");
        
        // Now process payment
        // But: If Order Service is slow, Payment Service is slow
        // If Order Service crashes, Payment Service can't process
        
        return new PaymentResponse("PROCESSED");
    }
}


// ✅ CORRECT: Loosely Coupled via Events

@Service
public class PaymentService {
    
    @Autowired
    private KafkaTemplate<String, PaymentEvent> kafkaTemplate;
    
    // Listen for order events (decoupled!)
    @KafkaListener(topics = "order.created")
    public void handleOrderCreated(OrderEvent event) {
        try {
            // Process payment based on event data
            processPayment(event.getOrderId(), event.getTotalAmount());
            
            // Publish result
            PaymentEvent result = new PaymentEvent(event.getOrderId(), "PROCESSED");
            kafkaTemplate.send("payment.processed", result);
        } catch (Exception e) {
            // Publish failure
            PaymentEvent failure = new PaymentEvent(event.getOrderId(), "FAILED");
            kafkaTemplate.send("payment.failed", failure);
        }
    }
}

// Benefits:
// ✅ Payment Service doesn't know about Order Service
// ✅ Payment Service doesn't depend on Order Service running
// ✅ Both services can scale independently
// ✅ Can replace Order Service without touching Payment Service
```

#### **2. High Cohesion**

```java
// ❌ WRONG: Mixed Responsibilities

@Service
public class OrderService {
    
    public void processOrder(OrderRequest req) {
        // Order creation (cohesive)
        Order order = createOrder(req);
        
        // Payment processing (NOT cohesive - should be Payment Service!)
        stripeClient.charge(order.getCustomer(), order.getAmount());
        
        // Inventory management (NOT cohesive - should be Inventory Service!)
        inventoryDb.reserveStock(order.getItems());
        
        // Email notification (NOT cohesive - should be Notification Service!)
        emailService.sendOrderConfirmation(order);
    }
}


// ✅ CORRECT: Single Responsibility

@Service
public class OrderService {
    
    @Autowired
    private KafkaTemplate<String, OrderEvent> kafkaTemplate;
    
    public Order processOrder(OrderRequest req) {
        // ONLY order-related operations
        Order order = createOrder(req);
        orderRepository.save(order);
        
        // Let other services handle their concerns
        OrderEvent event = new OrderEvent(order);
        kafkaTemplate.send("order.created", event);
        
        // Payment, Inventory, Notification services will handle it
        return order;
    }
}

// Benefits:
// ✅ Order Service focused on orders only
// ✅ Easy to understand, test, and maintain
// ✅ Easy to change order logic without affecting payment/inventory
```

#### **3. Autonomy**

```java
// Services should be able to:
// 1. Start independently
// 2. Run independently
// 3. Deploy independently
// 4. Scale independently

// Application in your project:

// Order Service can start without waiting for Payment Service
// Payment Service can be scaled (replicas) without affecting Order
// Inventory Service can be deployed without restarting Order Service
// Product Service can use different database tech than others

// Configuration for independent startup:

// application.yml in each service:
eureka:
  client:
    enabled: true
    register-with-eureka: true
    fetch-registry: true
    serviceUrl:
      defaultZone: http://discovery-server:8761/eureka/
    initialInstanceInfoReplicationIntervalSeconds: 40
    instanceInfoReplicationIntervalSeconds: 30
    
    # But can start even if Eureka is temporarily down!
    retry:
      initial-interval: 1000
      max-interval: 16000
      multiplier: 1.1
      max-elapsed-time: 60000  # Give up after 60 seconds

kafka:
  bootstrap-servers: kafka:29092
  
  # Handle Kafka failures gracefully
  retry:
    backoff:
      initial: 100
      max: 1000
      multiplier: 2
```

---

## 🎯 Microservices Design Patterns {#design-patterns}

### **1. API Composition Pattern**

When you need data from multiple services:

```java
// Scenario: Get complete order with customer and payment info

// ❌ WRONG: N+1 queries problem

@RestController
@RequestMapping("/orders")
public class OrderController {
    
    @GetMapping("/{orderId}")
    public OrderWithDetails getOrder(@PathVariable Long orderId) {
        // 1. Get order (1 query)
        Order order = orderService.getOrder(orderId);
        
        // 2. Get customer (N query)
        Customer customer = customerService.getCustomer(order.getCustomerId());
        
        // 3. Get payment (N query)
        Payment payment = paymentService.getPayment(orderId);
        
        // If order has 100 items, makes 102 queries! (N+1 problem)
        // Total: 1 + 1 + 1 = 3 queries minimum
        
        return new OrderWithDetails(order, customer, payment);
    }
}


// ✅ CORRECT: API Composition (fetch in parallel)

@RestController
@RequestMapping("/orders")
public class OrderController {
    
    @Autowired
    private RestTemplate restTemplate;
    
    @GetMapping("/{orderId}")
    public OrderWithDetails getOrder(@PathVariable Long orderId) {
        // Fetch from all services in parallel
        CompletableFuture<Order> orderFuture = 
            CompletableFuture.supplyAsync(() -> 
                orderService.getOrder(orderId));
        
        CompletableFuture<Customer> customerFuture = 
            CompletableFuture.supplyAsync(() -> 
                restTemplate.getForObject("http://customer-service:8081/api/customers/" + 
                    getOrderAsync().get().getCustomerId(), Customer.class));
        
        CompletableFuture<Payment> paymentFuture = 
            CompletableFuture.supplyAsync(() -> 
                restTemplate.getForObject("http://payment-service:8084/api/payments/" + 
                    orderId, Payment.class));
        
        // Wait for all (in parallel)
        CompletableFuture.allOf(orderFuture, customerFuture, paymentFuture).join();
        
        return new OrderWithDetails(
            orderFuture.join(),
            customerFuture.join(),
            paymentFuture.join()
        );
    }
}

// Better yet: Cache results and use CQRS pattern (see next sections)
```

### **2. CQRS Pattern (Command Query Responsibility Segregation)**

```java
// Separate reads from writes to optimize each independently

// ❌ TRADITIONAL: Single database for read and write

@Service
public class OrderService {
    
    @PostMapping("/orders")
    public Order createOrder(@RequestBody CreateOrderRequest req) {
        // WRITE operation
        Order order = new Order();
        order.setCustomerId(req.getCustomerId());
        orderDb.save(order);  // ← Write to main database
        return order;
    }
    
    @GetMapping("/orders")
    public List<Order> getAllOrders() {
        // READ operation  (slow query if table is huge!)
        return orderDb.findAll();  // ← Read from same database
    }
}


// ✅ CQRS: Separate read and write models

@Service
public class OrderCommandService {  // Handle WRITES
    
    @Autowired
    private OrderRepository orderDb;  // Write database (PostgreSQL)
    
    @Autowired
    private KafkaTemplate<String, OrderEvent> kafka;
    
    @PostMapping("/orders")
    public Order createOrder(@RequestBody CreateOrderRequest req) {
        Order order = new Order();
        order.setCustomerId(req.getCustomerId());
        orderDb.save(order);
        
        // Publish event for read model to consume
        kafka.send("order.created", new OrderEvent(order));
        
        return order;
    }
}

@Service
public class OrderQueryService {  // Handle READS
    
    @Autowired
    private OrderReadModelRepository readDb;  // Elasticsearch (optimized for reads)
    
    @GetMapping("/orders")
    public Page<OrderReadModel> getAllOrders(Pageable page) {
        // Read from optimized read model (fast!)
        return readDb.findAll(page);
    }
    
    @KafkaListener(topics = "order.created")
    public void handleOrderCreated(OrderEvent event) {
        // Update read model when write happens
        OrderReadModel readModel = convertToReadModel(event);
        readDb.save(readModel);  // Index in Elasticsearch
    }
}

// Benefits:
// ✅ Writes optimized for ACID transactions
// ✅ Reads optimized for speed (Elasticsearch)
// ✅ Can scale read replicas independently
// ✅ Read model is eventually consistent (eventual consistency)
```

### **3. Event Sourcing Pattern**

```java
// Store all changes as immutable events, not current state

// ❌ TRADITIONAL: Store current state only

@Entity
public class Order {
    @Id
    private Long id;
    private String status;  // CREATED, PAID, SHIPPED, DELIVERED
    private BigDecimal amount;
    @Temporal(TemporalType.TIMESTAMP)
    private Date updatedAt;
}

// Problem: If you update status from PAID to SHIPPED, 
// you lose history of when it became PAID!


// ✅ EVENT SOURCING: Store events (immutable history)

@Entity
public class OrderEvent {
    @Id
    @GeneratedValue
    private Long id;
    
    private Long orderId;
    private String eventType;  // OrderCreatedEvent, PaymentProcessedEvent, etc.
    
    @Column(columnDefinition = "TEXT")
    private String eventData;  // JSON payload
    
    private Long version;  // Version of this aggregate
    
    @CreationTimestamp
    private LocalDateTime createdAt;
}

// Example events for Order #123:
/*
Event 1: OrderCreatedEvent
  {
    "orderId": 123,
    "customerId": 5,
    "amount": 100.00,
    "items": [{"productId": 10, "qty": 2}],
    "timestamp": "2024-01-15T10:00:00Z"
  }

Event 2: PaymentProcessedEvent
  {
    "orderId": 123,
    "paymentId": 456,
    "amount": 100.00,
    "timestamp": "2024-01-15T10:05:00Z"
  }

Event 3: OrderShippedEvent
  {
    "orderId": 123,
    "shippingDate": "2024-01-16T09:00:00Z",
    "trackingNumber": "TRACK123",
    "timestamp": "2024-01-16T09:30:00Z"
  }

Event 4: OrderDeliveredEvent
  {
    "orderId": 123,
    "deliveryDate": "2024-01-18T14:00:00Z",
    "timestamp": "2024-01-18T14:15:00Z"
  }
*/

// Reconstructing current state:
@Service
public class OrderEventSourceService {
    
    public Order reconstructOrder(Long orderId) {
        // Get all events for this order
        List<OrderEvent> events = eventRepository.findByOrderId(orderId);
        
        // Replay events to reconstruct current state
        Order order = new Order();
        order.setId(orderId);
        
        for (OrderEvent event : events) {
            switch(event.getEventType()) {
                case "OrderCreatedEvent":
                    OrderCreatedEvent created = parseEvent(event, OrderCreatedEvent.class);
                    order.setCustomerId(created.getCustomerId());
                    order.setAmount(created.getAmount());
                    order.setStatus("CREATED");
                    break;
                    
                case "PaymentProcessedEvent":
                    order.setStatus("PAID");
                    break;
                    
                case "OrderShippedEvent":
                    OrderShippedEvent shipped = parseEvent(event, OrderShippedEvent.class);
                    order.setStatus("SHIPPED");
                    order.setTrackingNumber(shipped.getTrackingNumber());
                    break;
                    
                case "OrderDeliveredEvent":
                    order.setStatus("DELIVERED");
                    break;
            }
        }
        
        return order;
    }
    
    public void appendEvent(OrderEvent event) {
        // Never update events, only append
        eventRepository.save(event);
    }
}

// Benefits:
// ✅ Complete audit trail (know exactly what happened when)
// ✅ Can replay events to debug issues
// ✅ Can reconstruct state at any point in time
// ✅ Event-based communication naturally
```

---

## ⚡ Resilience Patterns {#resilience-patterns}

### **1. Circuit Breaker Pattern**

```java
// Prevent cascading failures by stopping calls to failing services

@Service
public class OrderService {
    
    @Autowired
    private RestTemplate restTemplate;
    
    // Circuit Breaker with Resilience4j
    @CircuitBreaker(
        name = "paymentServiceCircuitBreaker",
        fallbackMethod = "paymentServiceFallback"
    )
    public PaymentResponse callPaymentService(Long orderId) {
        // This will be automatically monitored
        return restTemplate.postForObject(
            "http://payment-service:8084/api/payments",
            new PaymentRequest(orderId),
            PaymentResponse.class
        );
    }
    
    // Fallback: called when circuit is OPEN or service fails
    public PaymentResponse paymentServiceFallback(Long orderId, Exception ex) {
        // Return graceful response instead of failing
        return new PaymentResponse(
            orderId,
            "PENDING",  // Mark as pending instead of failing
            "Payment service temporarily unavailable. Will retry later."
        );
    }
}

// Configuration:
/*
resilience4j:
  circuitbreaker:
    instances:
      paymentServiceCircuitBreaker:
        registerHealthIndicator: true
        slidingWindowType: COUNT_BASED
        slidingWindowSize: 10        # Last 10 calls
        failureRateThreshold: 50     # Open if 50% fail
        waitDurationInOpenState: 30000  # Wait 30 sec before retry
        permittedNumberOfCallsInHalfOpenState: 3  # Try 3 times in HALF_OPEN
        automaticTransitionFromOpenToHalfOpenEnabled: true

  retry:
    instances:
      paymentServiceCircuitBreaker:
        max-attempts: 3
        wait-duration: 1000
        retry-exceptions:
          - java.net.ConnectException
          - java.io.IOException

  timelimit:
    instances:
      paymentServiceCircuitBreaker:
        timeout-duration: 2s
*/

// States:
/*
CLOSED (Normal):
  ├─ Requests go through normally
  └─ Failure count = 0

If failures reach threshold:
  └─ → OPEN

OPEN (Circuit Broken):
  ├─ Requests fail immediately (no call to service)
  ├─ Use fallback method
  ├─ Wait configured time
  └─ → HALF_OPEN

HALF_OPEN (Testing):
  ├─ Allow limited calls to service
  ├─ If success → CLOSED
  └─ If fail → OPEN (reset timer)
*/
```

### **2. Retry Pattern with Exponential Backoff**

```java
// Automatically retry failed requests with increasing delays

@Service
public class PaymentService {
    
    @Autowired
    private StripeClient stripeClient;
    
    @Retry(
        name = "stripePaymentRetry",
        fallbackMethod = "paymentRetryFallback"
    )
    public StripeResponse chargeCard(String customerId, BigDecimal amount) {
        // Will retry automatically if fails
        return stripeClient.charge(customerId, amount);
    }
    
    public StripeResponse paymentRetryFallback(String customerId, 
                                                BigDecimal amount, Exception ex) {
        // After all retries failed, use fallback
        throw new PaymentException("All payment retries failed", ex);
    }
}

// Configuration:
/*
resilience4j:
  retry:
    instances:
      stripePaymentRetry:
        max-attempts: 3
        wait-duration: 1000
        interval-function: exponential
        exponential-random-backoff:
          initial-delay: 1000    # 1 second
          multiplier: 2          # Double each time
          max-random-wait: 1000  # Add random 0-1 sec
        retry-exceptions:
          - java.net.ConnectException
          - java.net.SocketTimeoutException
          - retrofit2.HttpException
        ignore-exceptions:
          - IllegalArgumentException  # Don't retry these
*/

// Execution timeline:
/*
T0:00 - First attempt
        → FAILS (Connection timeout)

T0:01 - Wait 1 second

T0:01 - Second attempt
        → FAILS (Connection timeout)

T0:03 - Wait 2 seconds (1 * 2)

T0:03 - Third attempt
        → SUCCESS! ✅

Total wait: 3 seconds instead of failing immediately!
*/
```

### **3. Timeout Pattern**

```java
// Prevent waiting forever for slow services

@Service
public class OrderService {
    
    @Autowired
    @Qualifier("timeoutRestTemplate")  // Special RestTemplate with timeout
    private RestTemplate restTemplate;
    
    public Order createOrder(CreateOrderRequest req) {
        // This call will timeout after 2 seconds
        try {
            Customer customer = restTemplate.getForObject(
                "http://customer-service:8081/api/customers/" + req.getCustomerId(),
                Customer.class
            );
        } catch (ResourceAccessException e) {
            if (e.getCause() instanceof SocketTimeoutException) {
                // Handle timeout
                throw new ServiceUnavailableException(
                    "Customer Service timed out after 2 seconds"
                );
            }
            throw e;
        }
    }
}

// Configuration:
/*
@Configuration
public class RestTemplateConfig {
    
    @Bean(name = "timeoutRestTemplate")
    public RestTemplate timeoutRestTemplate(
            ClientHttpRequestFactory clientHttpRequestFactory) {
        return new RestTemplate(clientHttpRequestFactory);
    }
    
    @Bean
    public ClientHttpRequestFactory clientHttpRequestFactory() {
        HttpComponentsClientHttpRequestFactory factory = 
            new HttpComponentsClientHttpRequestFactory();
        
        factory.setConnectTimeout(2000);      // Connect timeout: 2 sec
        factory.setReadTimeout(2000);         // Read timeout: 2 sec
        
        return factory;
    }
}
*/
```

### **4. Bulkhead Pattern**

```java
// Isolate resources to prevent one service from consuming all resources

@Service
public class PaymentService {
    
    // Thread pool 1: For Stripe charges
    @Autowired
    @Qualifier("stripeExecutor")
    private ExecutorService stripeExecutor;
    
    // Thread pool 2: For PayPal charges
    @Autowired
    @Qualifier("paypalExecutor")
    private ExecutorService paypalExecutor;
    
    public CompletableFuture<StripeResponse> chargeWithStripe(
            String customerId, BigDecimal amount) {
        // Uses dedicated Stripe thread pool (isolated)
        return CompletableFuture.supplyAsync(() -> 
            stripe.charge(customerId, amount), stripeExecutor);
    }
    
    public CompletableFuture<PayPalResponse> chargeWithPayPal(
            String customerId, BigDecimal amount) {
        // Uses dedicated PayPal thread pool (isolated)
        return CompletableFuture.supplyAsync(() -> 
            paypal.charge(customerId, amount), paypalExecutor);
    }
}

// Configuration:
/*
@Configuration
public class BulkheadConfig {
    
    @Bean(name = "stripeExecutor")
    public ExecutorService stripeExecutor() {
        // 10 threads for Stripe
        return Executors.newFixedThreadPool(10);
    }
    
    @Bean(name = "paypalExecutor")
    public ExecutorService paypalExecutor() {
        // 5 threads for PayPal
        return Executors.newFixedThreadPool(5);
    }
}

// Benefit:
// If Stripe is slow, it only uses 10 threads
// PayPal still has 5 threads available
// Other services not affected
*/
```

---

## 🔄 Data Consistency & Transactions {#data-consistency}

### **SAGA Pattern - Distributed Transactions**

```java
// Orchestration-based SAGA (Order Service orchestrates)

@Service
public class OrderOrchestrationSaga {
    
    @Autowired
    private OrderRepository orderRepository;
    
    @Autowired
    private RestTemplate restTemplate;
    
    @Autowired
    private KafkaTemplate<String, Event> kafka;
    
    public void executeOrderSaga(CreateOrderRequest req) {
        // Step 1: Create Order
        Order order = createOrder(req);
        if (order == null) {
            throw new Exception("Order creation failed");
        }
        
        try {
            // Step 2: Reserve Inventory
            InventoryResponse invResp = restTemplate.postForObject(
                "http://inventory-service:8082/api/inventory/reserve",
                new ReserveRequest(order.getId(), order.getItems()),
                InventoryResponse.class
            );
            
            if (!invResp.isSuccess()) {
                // Compensate: Rollback order
                orderRepository.delete(order);
                throw new Exception("Inventory reservation failed");
            }
            
            // Step 3: Process Payment
            PaymentResponse payResp = restTemplate.postForObject(
                "http://payment-service:8084/api/payments/charge",
                new ChargeRequest(order.getCustomerId(), order.getAmount()),
                PaymentResponse.class
            );
            
            if (!payResp.isSuccess()) {
                // Compensate: Release inventory
                restTemplate.postForObject(
                    "http://inventory-service:8082/api/inventory/release",
                    new ReleaseRequest(order.getId()),
                    ReleaseResponse.class
                );
                
                // Compensate: Rollback order
                orderRepository.delete(order);
                throw new Exception("Payment failed");
            }
            
            // All succeeded!
            order.setStatus(OrderStatus.CONFIRMED);
            orderRepository.save(order);
            
        } catch (Exception e) {
            // If any step fails, compensations already executed
            throw e;
        }
    }
}


// ❌ PROBLEM: Blocking synchronous calls
//    If service is slow, everything waits
//    If service crashes, compensation logic might not execute


// ✅ BETTER: Choreography-based SAGA (via Events)

@Service
public class OrderSagaChoreography {
    
    @Autowired
    private OrderRepository orderRepository;
    
    @Autowired
    private KafkaTemplate<String, Event> kafka;
    
    // Step 1: Create order
    @PostMapping("/orders")
    public Order createOrder(@RequestBody CreateOrderRequest req) {
        Order order = new Order();
        order.setCustomerId(req.getCustomerId());
        order.setStatus(OrderStatus.PENDING);
        Order saved = orderRepository.save(order);
        
        // Publish event (don't wait for response)
        OrderCreatedEvent event = new OrderCreatedEvent(saved);
        kafka.send("order.created", event);
        
        return saved;
    }
    
    // Step 2-3: Listen for compensation if needed
    @KafkaListener(topics = "payment.failed")
    public void handlePaymentFailed(PaymentFailedEvent event) {
        // Inventory Service listens and compensates
        // Order Service updates status to FAILED
        
        Order order = orderRepository.findById(event.getOrderId()).orElseThrow();
        order.setStatus(OrderStatus.FAILED);
        orderRepository.save(order);
    }
}

// Benefits:
// ✅ Services don't call each other directly
// ✅ Compensation logic distributed (each service knows how to compensate)
// ✅ Services process at their own pace
// ✅ No blocking waits
// ✅ If one service is down, others continue
```

### **Read-Your-Writes Consistency**

```java
// In distributed systems, reading immediately after write might return old data

@Service
public class OrderQueryService {
    
    @Autowired
    private RestTemplate restTemplate;
    
    // After creating order, user might not see it immediately
    public Order getOrderAfterCreation(Long orderId) {
        // Read from read-model (Elasticsearch)
        // Might be stale for a few milliseconds
        
        Order order = searchService.findById(orderId);
        if (order == null) {
            // Just created, might not be in read-model yet
            // Read from primary database instead
            return orderRepository.findById(orderId).orElse(null);
        }
        return order;
    }
}

// In your application:
/*
User creates order #123
  ↓
API returns: { "orderId": 123, "status": "PENDING" }
  ↓
User immediately views orders
  ↓
Read-model might not have it yet (eventual consistency)
  ↓
Solution: Return current state from write operation
         OR cache the response in client
         OR wait a bit before querying read-model
*/
```

---

## 📡 Communication Strategies {#communication}

### **REST APIs**

```java
// Synchronous, request-response, blocking

@RestController
@RequestMapping("/api/orders")
public class OrderController {
    
    // GET: Retrieve resource
    @GetMapping("/{orderId}")
    public ResponseEntity<Order> getOrder(@PathVariable Long orderId) {
        Order order = orderService.findById(orderId)
            .orElseThrow(() -> new NotFoundException("Order not found"));
        return ResponseEntity.ok(order);
    }
    
    // POST: Create resource
    @PostMapping
    public ResponseEntity<Order> createOrder(@RequestBody CreateOrderRequest req) {
        Order order = orderService.createOrder(req);
        return ResponseEntity.created(
            URI.create("/api/orders/" + order.getId())
        ).body(order);
    }
    
    // PUT: Replace resource
    @PutMapping("/{orderId}")
    public ResponseEntity<Order> updateOrder(@PathVariable Long orderId,
                                            @RequestBody UpdateOrderRequest req) {
        Order order = orderService.updateOrder(orderId, req);
        return ResponseEntity.ok(order);
    }
    
    // PATCH: Partial update
    @PatchMapping("/{orderId}")
    public ResponseEntity<Order> patchOrder(@PathVariable Long orderId,
                                           @RequestBody Map<String, Object> patches) {
        Order order = orderService.patchOrder(orderId, patches);
        return ResponseEntity.ok(order);
    }
    
    // DELETE: Remove resource
    @DeleteMapping("/{orderId}")
    public ResponseEntity<Void> deleteOrder(@PathVariable Long orderId) {
        orderService.delete(orderId);
        return ResponseEntity.noContent().build();
    }
}

// When to use REST:
// ✅ Simple request-response interactions
// ✅ CRUD operations (Create, Read, Update, Delete)
// ✅ Resource queries
// ❌ Long-running operations (use async instead)
// ❌ Fire-and-forget operations (use events instead)
```

### **Message Queue (Kafka)**

```java
// Asynchronous, event-based, non-blocking

// Producer (publishes events)
@Service
public class OrderService {
    
    @Autowired
    private KafkaTemplate<String, OrderEvent> kafkaTemplate;
    
    public Order createOrder(CreateOrderRequest req) {
        Order order = orderRepository.save(new Order(req));
        
        OrderEvent event = OrderEvent.builder()
            .orderId(order.getId())
            .customerId(order.getCustomerId())
            .totalAmount(order.getTotalAmount())
            .items(order.getItems())
            .build();
        
        // Send to Kafka (doesn't wait for response)
        ListenableFuture<SendResult<String, OrderEvent>> future =
            kafkaTemplate.send("order.created", event);
        
        // Optionally handle completion
        future.addCallback(
            result -> System.out.println("Event sent successfully"),
            ex -> System.out.println("Event failed: " + ex.getMessage())
        );
        
        return order;
    }
}

// Consumer (listens for events)
@Service
public class PaymentService {
    
    @Autowired
    private PaymentRepository paymentRepository;
    
    @KafkaListener(
        topics = "order.created",
        groupId = "payment-service-group",
        containerFactory = "kafkaListenerContainerFactory"
    )
    public void handleOrderCreated(OrderEvent event) throws Exception {
        try {
            // Process payment
            PaymentResult result = processPayment(event);
            
            // Publish result
            kafkaTemplate.send("payment.processed", 
                new PaymentEvent(event.getOrderId(), result));
        } catch (Exception e) {
            // Publish failure
            kafkaTemplate.send("payment.failed",
                new PaymentFailedEvent(event.getOrderId(), e.getMessage()));
            
            throw e;  // Mark message as failed (will retry)
        }
    }
}

// Configuration:
/*
kafka:
  bootstrap-servers: kafka:29092
  
  producer:
    key-serializer: org.apache.kafka.common.serialization.StringSerializer
    value-serializer: org.springframework.kafka.support.serializer.JsonSerializer
    acks: all  # Wait for all replicas to acknowledge
    retries: 3
    linger-ms: 10  # Batch messages
  
  consumer:
    bootstrap-servers: kafka:29092
    group-id: payment-service-group
    key-deserializer: org.apache.kafka.common.serialization.StringDeserializer
    value-deserializer: org.springframework.kafka.support.serializer.JsonDeserializer
    auto-offset-reset: earliest  # Start from beginning if no offset
    enable-auto-commit: false  # Manual offset commit
    max-poll-records: 100
*/

// When to use Kafka:
// ✅ Asynchronous processing
// ✅ Decoupling services
// ✅ Event streaming
// ✅ Fan-out (multiple consumers)
// ✅ Guaranteed delivery (persistent)
// ❌ Request-response patterns (use REST)
// ❌ Real-time critical operations (REST is faster)
```

### **Service-to-Service Communication**

```java
// Using RestTemplate with Eureka discovery

@Configuration
public class RestTemplateConfig {
    
    @Bean
    public RestTemplate restTemplate(RestTemplateBuilder builder) {
        return builder
            .setConnectTimeout(Duration.ofSeconds(2))
            .setReadTimeout(Duration.ofSeconds(2))
            .errorHandler(new CustomErrorHandler())
            .build();
    }
}

@Service
public class PaymentService {
    
    @Autowired
    private RestTemplate restTemplate;
    
    public Order getOrderFromOrderService(Long orderId) {
        // Eureka automatically finds Order Service URL
        String url = "http://order-service/api/orders/" + orderId;
        
        try {
            return restTemplate.getForObject(url, Order.class);
        } catch (RestClientException e) {
            // Handle failure gracefully
            throw new ServiceUnavailableException(
                "Could not reach Order Service", e);
        }
    }
}

// Using OpenFeign (declarative HTTP client)

@FeignClient(
    name = "order-service",
    url = "${feign.order-service.url:http://order-service:8083}",
    fallback = OrderServiceFallback.class
)
public interface OrderServiceClient {
    
    @GetMapping("/api/orders/{orderId}")
    Order getOrder(@PathVariable("orderId") Long orderId);
    
    @PostMapping("/api/orders")
    Order createOrder(@RequestBody CreateOrderRequest req);
}

// Fallback when service is down
@Component
public class OrderServiceFallback implements OrderServiceClient {
    
    @Override
    public Order getOrder(Long orderId) {
        return new Order(orderId, "UNKNOWN_STATUS");
    }
    
    @Override
    public Order createOrder(CreateOrderRequest req) {
        throw new ServiceUnavailableException("Order Service is currently unavailable");
    }
}

// Usage:
@Service
public class PaymentService {
    
    @Autowired
    private OrderServiceClient orderClient;  // Injected automatically
    
    public void processPayment(Long orderId) {
        Order order = orderClient.getOrder(orderId);  // Automatic call
        // Process payment...
    }
}
```

---

## 📊 Monitoring & Observability {#monitoring}

### **Structured Logging**

```java
// Use structured logging for better searchability

@Slf4j
@Service
public class OrderService {
    
    public Order createOrder(CreateOrderRequest req) {
        // Use MDC (Mapped Diagnostic Context) for correlation IDs
        String correlationId = UUID.randomUUID().toString();
        MDC.put("correlationId", correlationId);
        MDC.put("userId", req.getCustomerId());
        MDC.put("service", "order-service");
        
        try {
            log.info("Creating order", 
                "customerId", req.getCustomerId(),
                "itemCount", req.getItems().size(),
                "totalAmount", req.getTotalAmount()
            );
            
            Order order = orderRepository.save(new Order(req));
            
            log.info("Order created successfully",
                "orderId", order.getId(),
                "status", "CREATED"
            );
            
            return order;
        } catch (Exception e) {
            log.error("Order creation failed",
                "error", e.getMessage(),
                "errorType", e.getClass().getName()
            );
            throw e;
        } finally {
            MDC.clear();  // Clean up
        }
    }
}

// Structured log output (JSON):
/*
{
  "timestamp": "2024-01-15T10:30:00.000Z",
  "level": "INFO",
  "logger": "OrderService",
  "message": "Order created successfully",
  "correlationId": "a1b2c3d4",
  "userId": "5",
  "service": "order-service",
  "orderId": "123",
  "status": "CREATED"
}
*/

// Log aggregation in Elasticsearch:
// Can search by: correlationId, userId, service, errorType, etc.
```

### **Metrics Collection**

```java
// Collect metrics with Micrometer

@Service
public class OrderService {
    
    @Autowired
    private MeterRegistry meterRegistry;
    
    public Order createOrder(CreateOrderRequest req) {
        Timer.Sample sample = Timer.start(meterRegistry);
        
        try {
            // Track operation
            order_creation_total.increment();
            
            Order order = orderRepository.save(new Order(req));
            
            // Track success
            order_creation_success_total.increment();
            meterRegistry.gauge("order.total_created", getOrderCount());
            
            return order;
        } catch (Exception e) {
            // Track failure
            order_creation_failure_total.increment(
                Tag.of("reason", e.getClass().getSimpleName())
            );
            throw e;
        } finally {
            sample.stop(Timer.builder("order.creation.time")
                .description("Time to create order")
                .publishPercentiles(0.5, 0.95, 0.99)  // P50, P95, P99
                .register(meterRegistry));
        }
    }
}

// Configuration:
/*
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus
  
  metrics:
    export:
      prometheus:
        enabled: true
    
    tags:
      application: micro-ecommerce
      environment: production
*/

// Metrics available at:
// GET localhost:8083/actuator/metrics
// GET localhost:8083/actuator/prometheus (Prometheus format)
```

### **Distributed Tracing**

```java
// Track requests across multiple services with Sleuth + Zipkin

@Slf4j
@Service
public class OrderService {
    
    @Autowired
    private RestTemplate restTemplate;
    
    public Order createOrder(CreateOrderRequest req) {
        // Sleuth automatically adds trace ID and span ID
        log.info("Creating order");  // Logs include traceId and spanId
        
        Order order = orderRepository.save(new Order(req));
        
        // Call another service
        // Trace ID automatically propagated via X-B3-* headers
        Customer customer = restTemplate.getForObject(
            "http://customer-service/api/customers/" + req.getCustomerId(),
            Customer.class
        );
        
        return order;
    }
}

// Trace flow in Zipkin (UI at localhost:9411):
/*
Trace ID: a1b2c3d4e5f6

Spans:
├─ api-gateway (0-10ms)
│  ├─ JWT validation (1-2ms)
│  └─ Route to order-service (2-8ms)
│
├─ order-service (10-150ms)
│  ├─ Database save (10-50ms)
│  ├─ Call customer-service (50-140ms)
│  │
│  └─ customer-service (55-135ms)
│     ├─ Database query (55-90ms)
│     └─ Return response (90-135ms)
│
└─ Respond to client (150-155ms)

Total request time: 155ms
Can see exactly where time spent!
*/

// Configuration:
/*
spring:
  sleuth:
    sampler:
      probability: 1.0  # Sample 100% of requests
  
  zipkin:
    base-url: http://zipkin:9411/
    sender:
      type: web
*/
```

---

## 🔐 Security Best Practices {#security}

### **Authentication & Authorization**

```java
// JWT-based authentication

@Configuration
@EnableWebSecurity
public class SecurityConfig {
    
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf().disable()
            .authorizeRequests()
            .antMatchers("/api/public/**").permitAll()
            .antMatchers("/api/customers/**").hasRole("USER")
            .antMatchers("/api/admin/**").hasRole("ADMIN")
            .anyRequest().authenticated()
            .and()
            .oauth2ResourceServer()
            .jwt()
            .jwtAuthenticationConverter(jwtAuthenticationConverter());
        
        return http.build();
    }
    
    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        JwtGrantedAuthoritiesConverter authoritiesConverter = 
            new JwtGrantedAuthoritiesConverter();
        
        authoritiesConverter.setAuthoritiesClaimName("roles");
        authoritiesConverter.setAuthorityPrefix("ROLE_");
        
        converter.setJwtGrantedAuthoritiesConverter(authoritiesConverter);
        return converter;
    }
}

// API Controller with authorization
@RestController
@RequestMapping("/api/orders")
public class OrderController {
    
    @PostMapping
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<Order> createOrder(@RequestBody CreateOrderRequest req,
                                            @CurrentUser User currentUser) {
        // Only authenticated users with USER role can create orders
        // currentUser is the authenticated user
        Order order = orderService.createOrder(req, currentUser.getId());
        return ResponseEntity.ok(order);
    }
    
    @GetMapping("/{orderId}")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<Order> getOrder(@PathVariable Long orderId,
                                          @CurrentUser User currentUser) {
        // Verify user owns this order
        Order order = orderService.findById(orderId)
            .filter(o -> o.getCustomerId().equals(currentUser.getId()))
            .orElseThrow(() -> new ForbiddenException("Access denied"));
        
        return ResponseEntity.ok(order);
    }
}
```

### **Secrets Management**

```yaml
# ❌ WRONG: Secrets in application.yml

spring:
  datasource:
    username: postgres
    password: mySecretPassword123  # ← EXPOSED!
  
  kafka:
    security:
      protocol: SASL_SSL
      sasl:
        username: kafka-user
        password: kafkaPassword456  # ← EXPOSED!

# ✅ CORRECT: Secrets from environment variables

spring:
  datasource:
    username: ${DB_USERNAME}
    password: ${DB_PASSWORD}
  
  kafka:
    security:
      protocol: SASL_SSL
      sasl:
        username: ${KAFKA_USERNAME}
        password: ${KAFKA_PASSWORD}

# Or use HashiCorp Vault
# spring-cloud-vault-config automatically manages secrets

vault:
  host: vault-server:8200
  port: 8200
  scheme: http
  authentication: TOKEN
  token: ${VAULT_TOKEN}
  kv-version: 2
```

### **API Rate Limiting**

```java
// Prevent DoS attacks and abuse

@Configuration
public class RateLimitConfig {
    
    @Bean
    public KeyResolver userKeyResolver() {
        // Rate limit per user
        return exchange -> Mono.just(
            exchange.getRequest()
                .getHeaders()
                .getFirst("X-User-Id")
        );
    }
    
    @Bean
    public RateLimiter rateLimiter() {
        return RateLimiter.create(10.0);  // 10 requests per second
    }
}

@RestController
@RequestMapping("/api/orders")
public class OrderController {
    
    @Autowired
    private RateLimiter rateLimiter;
    
    @PostMapping
    public ResponseEntity<Order> createOrder(@RequestBody CreateOrderRequest req,
                                            HttpServletRequest httpReq) {
        String userId = httpReq.getHeader("X-User-Id");
        
        // Acquire permit (non-blocking)
        if (!rateLimiter.tryAcquire()) {
            return ResponseEntity.status(429)  // Too Many Requests
                .body(new ErrorResponse("Rate limit exceeded. Max 10 req/sec"));
        }
        
        Order order = orderService.createOrder(req);
        return ResponseEntity.ok(order);
    }
}
```

---

## 🚀 Production Deployment {#deployment}

### **12-Factor App Principles for Microservices**

```yaml
# 1. Codebase: One codebase tracked in version control
# 2. Dependencies: Explicitly declared (Maven/Gradle)
# 3. Config: Environment variables, not hardcoded
# 4. Backing Services: Treat as attached resources
# 5. Build/Run: Separate build and run stages
# 6. Processes: Stateless execution
# 7. Port Binding: Export HTTP via port binding
# 8. Concurrency: Horizontal scaling via multiple processes
# 9. Disposability: Fast startup and shutdown
# 10. Dev/Prod Parity: Same tools everywhere
# 11. Logs: Write to stdout (collected by docker)
# 12. Admin Tasks: Run as one-off processes

# In your project:

application.yml:
  spring:
    profiles:
      active: ${SPRING_PROFILE:dev}
  
  # Config from environment
  datasource:
    url: jdbc:postgresql://${DB_HOST}:${DB_PORT}/${DB_NAME}
    username: ${DB_USERNAME}
    password: ${DB_PASSWORD}
  
  kafka:
    bootstrap-servers: ${KAFKA_BOOTSTRAP_SERVERS}
  
  # Logs to stdout (Docker collects)
  logging:
    level:
      root: INFO
      com.ecommerce: DEBUG
    format: '{"time":"%d","level":"%p","logger":"%c","message":"%m"}%n'
```

### **Deployment Checklist**

```
Pre-deployment:
───────────────
□ All tests passing (unit, integration)
□ Code reviewed and merged
□ Security scan completed
□ Dependencies updated
□ Configuration validated
□ Database migrations tested
□ Monitoring configured
□ Alerts set up

Deployment:
───────────
□ Build Docker images
□ Push to Docker registry
□ Tag with version number
□ Update docker-compose.yml
□ Run database migrations
□ Deploy to staging
□ Run smoke tests
□ Deploy to production (canary/blue-green)

Post-deployment:
────────────────
□ Verify all services are running
□ Check metrics and dashboards
□ Monitor error rates
□ Monitor latency
□ Check logs for errors
□ Test critical flows end-to-end
□ Notify stakeholders
□ Document deployment
```

---

## 🌍 Real-World Scenarios {#scenarios}

### **Scenario 1: Service Upgrade Without Downtime**

```
Before: Monolith
──────────────
All users → Wait for deployment → 30-minute downtime

After: Microservices
──────────────────

Upgrade Order Service from v1.0 to v1.1:

Step 1: Deploy canary (10% traffic)
    ├─ New version (v1.1) receives 10% of order requests
    ├─ Monitoring checks for errors/latency
    ├─ All OK? → Continue to step 2
    └─ Errors? → Rollback canary

Step 2: Deploy to 50%
    ├─ New version receives 50% of traffic
    ├─ Continue monitoring
    └─ All OK? → Continue to step 3

Step 3: Deploy to 100%
    ├─ New version receives all traffic
    ├─ v1.0 instances shut down gracefully
    └─ Customers experience ZERO downtime!

Timeline:
T0:00 - v1.0 handling all requests
T0:10 - Canary deployed (v1.1 @ 10%)
T0:15 - All OK, scale to 50%
T0:20 - All OK, scale to 100%
T0:25 - v1.0 completely replaced
Total downtime: 0 minutes! ✅
```

### **Scenario 2: Handling Peak Load (Black Friday)**

```
Normal capacity:
────────────────
Order Service: 1 instance @ 1000 req/min

Black Friday: 10x traffic
───────────────────────
Order Service: Auto-scaled to 10 instances @ 10,000 req/min

How:
├─ Kubernetes monitors CPU usage
├─ When > 80%: Spin up new Order Service instance
├─ Load balancer distributes traffic
├─ Payment Service also auto-scales
├─ Inventory Service auto-scales
├─ Database auto-scales (read replicas)
│
└─ Result: Handles 10x traffic without crashing!

Without microservices: Would need to overprovision ALL components
With microservices: Scale only the bottleneck!
```

### **Scenario 3: Graceful Degradation**

```
Scenario: Stripe API is down

Normal flow:
    User places order
    └─ Order Service publishes OrderCreatedEvent
       └─ Payment Service charges card
          └─ Success! ✅

With Stripe down:
    User places order
    └─ Order Service publishes OrderCreatedEvent
       └─ Payment Service tries to charge
          └─ Stripe API TIMEOUT ❌
             └─ Circuit Breaker opens
                └─ Return "Pending" status
                   └─ Publish PaymentPendingEvent
                      └─ Scheduled job retries every 5 minutes
                         └─ When Stripe comes back: Process payment ✅
                            └─ Notify customer

Result: Order still created!
        Customers informed it's pending
        Payment processed when Stripe recovers
        No lost orders! ✅
```

---

## 💻 Complete Code Examples {#code}

### **Order Creation with SAGA Pattern**

```java
// Event definitions
public class OrderCreatedEvent {
    private Long orderId;
    private Long customerId;
    private BigDecimal totalAmount;
    private List<OrderItem> items;
    // getters/setters
}

public class PaymentProcessedEvent {
    private Long orderId;
    private String status;  // SUCCESS or FAILED
    // getters/setters
}

public class InventoryReservedEvent {
    private Long orderId;
    private String status;  // SUCCESS or FAILED
    // getters/setters
}

// Order Service
@Service
@Slf4j
public class OrderService {
    
    @Autowired
    private OrderRepository orderRepository;
    
    @Autowired
    private KafkaTemplate<String, OrderCreatedEvent> kafkaTemplate;
    
    @Transactional
    public Order createOrder(CreateOrderRequest req) {
        // Create order
        Order order = new Order();
        order.setCustomerId(req.getCustomerId());
        order.setStatus(OrderStatus.CREATED);
        order.setItems(req.getItems());
        order.setTotalAmount(calculateTotal(req.getItems()));
        
        Order saved = orderRepository.save(order);
        
        // Publish event
        OrderCreatedEvent event = OrderCreatedEvent.builder()
            .orderId(saved.getId())
            .customerId(saved.getCustomerId())
            .totalAmount(saved.getTotalAmount())
            .items(saved.getItems())
            .build();
        
        kafkaTemplate.send("order.created", event);
        
        return saved;
    }
    
    @KafkaListener(topics = "payment.processed")
    public void handlePaymentProcessed(PaymentProcessedEvent event) {
        Order order = orderRepository.findById(event.getOrderId()).orElseThrow();
        
        if ("SUCCESS".equals(event.getStatus())) {
            order.setStatus(OrderStatus.PAYMENT_CONFIRMED);
        } else {
            order.setStatus(OrderStatus.PAYMENT_FAILED);
        }
        
        orderRepository.save(order);
    }
}

// Payment Service
@Service
@Slf4j
public class PaymentService {
    
    @Autowired
    private PaymentRepository paymentRepository;
    
    @Autowired
    private StripeClient stripeClient;
    
    @Autowired
    private KafkaTemplate<String, PaymentProcessedEvent> kafkaTemplate;
    
    @KafkaListener(topics = "order.created")
    public void handleOrderCreated(OrderCreatedEvent event) {
        try {
            log.info("Processing payment for order: {}", event.getOrderId());
            
            // Charge payment gateway
            StripeResponse response = stripeClient.charge(
                event.getCustomerId(),
                event.getTotalAmount()
            );
            
            if (response.isSuccessful()) {
                // Save payment
                Payment payment = new Payment();
                payment.setOrderId(event.getOrderId());
                payment.setAmount(event.getTotalAmount());
                payment.setStatus(PaymentStatus.COMPLETED);
                paymentRepository.save(payment);
                
                // Publish success
                PaymentProcessedEvent result = PaymentProcessedEvent.builder()
                    .orderId(event.getOrderId())
                    .status("SUCCESS")
                    .build();
                kafkaTemplate.send("payment.processed", result);
                
                log.info("Payment successful for order: {}", event.getOrderId());
            } else {
                throw new PaymentException("Stripe declined: " + response.getError());
            }
        } catch (Exception e) {
            log.error("Payment failed for order: {}", event.getOrderId(), e);
            
            // Publish failure
            PaymentProcessedEvent failure = PaymentProcessedEvent.builder()
                .orderId(event.getOrderId())
                .status("FAILED")
                .build();
            kafkaTemplate.send("payment.processed", failure);
        }
    }
}

// Inventory Service (Compensation)
@Service
@Slf4j
public class InventoryService {
    
    @Autowired
    private InventoryRepository inventoryRepository;
    
    @Autowired
    private KafkaTemplate<String, InventoryReservedEvent> kafkaTemplate;
    
    @KafkaListener(topics = "order.created")
    public void handleOrderCreated(OrderCreatedEvent event) {
        try {
            log.info("Reserving inventory for order: {}", event.getOrderId());
            
            boolean reserved = reserveInventory(event.getItems());
            
            if (reserved) {
                // Publish success
                InventoryReservedEvent result = InventoryReservedEvent.builder()
                    .orderId(event.getOrderId())
                    .status("SUCCESS")
                    .build();
                kafkaTemplate.send("inventory.reserved", result);
                
                log.info("Inventory reserved for order: {}", event.getOrderId());
            } else {
                throw new InventoryException("Insufficient stock");
            }
        } catch (Exception e) {
            log.error("Inventory reservation failed for order: {}", event.getOrderId(), e);
            
            // Trigger compensation
            InventoryReservedEvent failure = InventoryReservedEvent.builder()
                .orderId(event.getOrderId())
                .status("FAILED")
                .build();
            kafkaTemplate.send("inventory.failed", failure);
        }
    }
    
    @KafkaListener(topics = "payment.failed")
    public void handlePaymentFailed(PaymentProcessedEvent event) {
        if ("FAILED".equals(event.getStatus())) {
            // Compensation: release inventory
            releaseInventory(event.getOrderId());
            log.info("Inventory released for order: {}", event.getOrderId());
        }
    }
}
```

---

## 🔧 Troubleshooting Guide {#troubleshooting}

### **Complete Debugging Workflow**

```bash
# 1. Check all services are running
docker-compose ps

# 2. Check service logs
docker-compose logs order-service | tail -50

# 3. Check if service is registered in Eureka
curl http://localhost:8761/eureka/apps/ORDER-SERVICE

# 4. Check service health
curl http://localhost:8083/actuator/health

# 5. Check database connectivity
docker-compose exec order-service bash
psql -h postgres -U postgres -d order_db -c "SELECT 1"

# 6. Check Kafka connectivity
docker-compose exec kafka kafka-console-consumer \
  --bootstrap-servers localhost:9092 --list-topics

# 7. Check metrics
curl http://localhost:8083/actuator/prometheus | grep order

# 8. Check traces in Zipkin
# Open browser: http://localhost:9411

# 9. Check logs in Kibana
# Open browser: http://localhost:5601

# 10. Restart failed service
docker-compose restart order-service
```

---

## ✅ Production Readiness Checklist

```
Architecture:
└─ ✅ Microservices properly scoped
   ✅ Clear API contracts
   ✅ Event-driven communication
   ✅ Database per service

Resilience:
└─ ✅ Circuit breakers implemented
   ✅ Retry logic with backoff
   ✅ Timeouts configured
   ✅ Bulkhead pattern for isolation
   ✅ Graceful degradation

Data:
└─ ✅ SAGA pattern for transactions
   ✅ Data consistency strategy
   ✅ Backup/recovery procedures
   ✅ Database migrations planned

Security:
└─ ✅ JWT authentication
   ✅ Authorization checks
   ✅ Secrets management (no hardcoded passwords)
   ✅ HTTPS/TLS enabled
   ✅ API rate limiting
   ✅ Input validation

Monitoring:
└─ ✅ Structured logging
   ✅ Metrics collection
   ✅ Distributed tracing
   ✅ Alerting rules
   ✅ Dashboards created

Operations:
└─ ✅ Auto-scaling configured
   ✅ Deployment pipeline set up
   ✅ Rollback procedures documented
   ✅ On-call runbooks created
   ✅ Load testing completed
   ✅ Disaster recovery plan
```

---

**Congratulations!** You now have comprehensive knowledge of microservices architecture, patterns, and production practices. Your micro-eCommerce project is a textbook example of all these concepts in action! 🎓

