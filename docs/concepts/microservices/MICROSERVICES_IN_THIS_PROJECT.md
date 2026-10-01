# Microservices In This Project 🎯
## Understanding YOUR micro-eCommerce Architecture

---

## 🏗️ Your Project Architecture Overview

Your **micro-eCommerce** project is a PERFECT REAL-WORLD example of microservices architecture! Let's break down exactly how it uses all the concepts from Level 1.

```
┌─────────────────────────────────────────────────────────────────────────┐
│                   YOUR MICRO-ECOMMERCE SYSTEM                          │
├─────────────────────────────────────────────────────────────────────────┤
│                                                                         │
│  ┌─────────────────────────────────────────────────────────────────┐  │
│  │                    CLIENT APPLICATIONS                         │  │
│  │           (Web Browser, Mobile App, etc.)                      │  │
│  └────────────────────────┬────────────────────────────────────────┘  │
│                           │                                           │
│                           │ All requests go to ONE place              │
│                           ▼                                           │
│          ┌─────────────────────────────────┐                         │
│          │    🚪 API GATEWAY (Port 8080)   │                         │
│          │  ┌─ JWT Authentication          │                         │
│          │  ├─ Request Routing             │                         │
│          │  ├─ Rate Limiting               │                         │
│          │  └─ Load Balancing              │                         │
│          └────────┬────────────────────────┘                         │
│                   │                                                  │
│     ┌─────────────┼─────────────────────────────────┐               │
│     ▼             ▼             ▼          ▼        ▼               │
│  ┌──────┐  ┌──────────┐  ┌──────────┐  ┌──────┐  ┌──────────┐      │
│  │Cust  │  │  Order   │  │ Payment  │  │Invent│  │ Product  │      │
│  │Service│  │ Service  │  │ Service  │  │ory   │  │ Service  │      │
│  │:8081 │  │ :8083    │  │ :8084    │  │:8082 │  │ :8085    │      │
│  └──┬───┘  └────┬─────┘  └────┬─────┘  └──┬───┘  └────┬─────┘      │
│     │           │             │           │          │             │
│     ▼           ▼             ▼           ▼          ▼             │
│  ┌─────┐  ┌──────────┐  ┌──────────┐  ┌──────┐  ┌──────────┐      │
│  │Cust │  │  Order   │  │ Payment  │  │Inv   │  │ Product  │      │
│  │DB   │  │   DB     │  │   DB     │  │  DB  │  │   DB     │      │
│  │PG   │  │   PG     │  │   PG     │  │ Mongo│  │   PG     │      │
│  └─────┘  └──────────┘  └──────────┘  └──────┘  └──────────┘      │
│                                                                    │
│  ┌────────────────────────────────────────────────────────────┐   │
│  │             🔄 EVENT BUS (Apache Kafka)                   │   │
│  │  Connects all services via Events (Async Communication)   │   │
│  │                                                           │   │
│  │  Topics:                                                │   │
│  │  ├─ order.created ─→ Payment & Inventory listen        │   │
│  │  ├─ payment.processed ─→ Order & Inventory listen      │   │
│  │  ├─ inventory.reserved ─→ Order listens                │   │
│  │  └─ inventory.failed ─→ Payment listens (refund)       │   │
│  └────────────────────────────────────────────────────────────┘   │
│                                                                    │
│  ┌──────────────────────────────────────────────────────────┐   │
│  │        💾 INFRASTRUCTURE SERVICES                        │   │
│  │                                                           │   │
│  │  ├─ 🔍 Service Discovery (Eureka)      :8761            │   │
│  │  │   (Services register & find each other)              │   │
│  │  │                                                       │   │
│  │  ├─ ⚙️  Config Server                    :8888          │   │
│  │  │   (Central configuration management)                 │   │
│  │  │                                                       │   │
│  │  ├─ 📊 Prometheus Metrics                :9090          │   │
│  │  │   (Monitoring & alerting)                            │   │
│  │  │                                                       │   │
│  │  ├─ 📈 Grafana Dashboards               :3000          │   │
│  │  │   (Visual metrics & monitoring)                      │   │
│  │  │                                                       │   │
│  │  ├─ 🔍 Elasticsearch                    :9200          │   │
│  │  ├─ 📝 Logstash                         :5000          │   │
│  │  └─ 🔎 Kibana Logs                      :5601          │   │
│  │   (Centralized logging & search)                        │   │
│  │                                                           │   │
│  └──────────────────────────────────────────────────────────┘   │
│                                                                    │
└────────────────────────────────────────────────────────────────────┘

Total: 5 Microservices + 3 Infrastructure Services + 8 Supporting Services
       = 16 Containers Running Together! 🐳
```

---

## 📋 Your 5 Core Microservices Explained

### 🎤 **Customer Service** (Port 8081)

**What it does:**
- Manages customer accounts
- Handles user registration & login
- Stores customer profiles and preferences
- Tracks customer orders (read-only reference)

**Its Database:** PostgreSQL `customer_db`
```sql
-- customer_db tables
CREATE TABLE customers (
    id BIGINT PRIMARY KEY,
    name VARCHAR(255),
    email VARCHAR(255) UNIQUE,
    phone VARCHAR(20),
    address TEXT,
    created_at TIMESTAMP,
    updated_at TIMESTAMP
);

CREATE TABLE customer_preferences (
    id BIGINT PRIMARY KEY,
    customer_id BIGINT,
    preferred_category VARCHAR(100),
    newsletter_subscribed BOOLEAN
);
```

**How it communicates:**
```
❌ WRONG: Directly query Order DB
SELECT * FROM order_db.orders WHERE customer_id = 5

✅ RIGHT: Asks Order Service via REST API
GET http://order-service:8083/api/orders?customerId=5

✅ ALSO RIGHT: Listens for events from Kafka
Topic: order.created → Updates customer order count
```

**Real Failure Scenario:**
```
❌ PROBLEM: Customer Service tries to connect to Discovery Server

Customer Service starts:
  ↓
  Registers with Eureka: "I'm Customer Service at 8081"
  ↓ (But Eureka is down!)
  ✋ FAILED TO REGISTER
  ↓
  Service starts anyway (with default config)
  ↓
  Payment Service can't find it!
  ↓
  Requests fail: "Customer Service not found"

✅ SOLUTION: Retry with exponential backoff

application.yml (in Customer Service):
eureka:
  client:
    register-with-eureka: true
    fetch-registry: true
    serviceUrl:
      defaultZone: http://discovery-server:8761/eureka/
    initialInstanceInfoReplicationIntervalSeconds: 40
    instanceInfoReplicationIntervalSeconds: 30
    
    registry-fetch-interval-seconds: 30  # Retry every 30 seconds
```

---

### 📦 **Order Service** (Port 8083)

**What it does:**
- Creates new orders
- Updates order status (CREATED → PAID → SHIPPED → DELIVERED)
- Tracks order history
- Publishes order events for other services

**Its Database:** PostgreSQL `order_db`
```sql
-- order_db tables
CREATE TABLE orders (
    id BIGINT PRIMARY KEY,
    customer_id BIGINT,
    order_status VARCHAR(50),  -- CREATED, PAID, SHIPPED, DELIVERED
    total_amount DECIMAL(10, 2),
    created_at TIMESTAMP,
    updated_at TIMESTAMP
);

CREATE TABLE order_items (
    id BIGINT PRIMARY KEY,
    order_id BIGINT,
    product_id BIGINT,
    quantity INT,
    unit_price DECIMAL(10, 2)
);
```

**How it communicates:**
```
┌────────────────────────────────────────┐
│     CLIENT CREATES ORDER               │
│  POST /api/orders                      │
│  {                                     │
│    "customerId": 5,                    │
│    "items": [{"productId": 10, "qty":2}]
│  }                                     │
└────────┬─────────────────────────────────┘
         ▼
    Order Service:
    1. Creates Order with status = CREATED
    2. Saves to order_db
    3. Publishes Event: "OrderCreatedEvent"
       
         ▼ (async via Kafka)
    
    Kafka Topic: order.created
    {
      "orderId": 123,
      "customerId": 5,
      "totalAmount": 100.00,
      "items": [...]
    }
    
    ├─→ Payment Service listens
    │   "Process payment for this order"
    │
    └─→ Inventory Service listens
        "Reserve stock for this order"
```

**Real Code Example (Order Service Event Publishing):**
```java
@Service
public class OrderService {
    @Autowired
    private OrderRepository orderRepository;
    
    @Autowired
    private KafkaTemplate<String, OrderEvent> kafkaTemplate;
    
    public Order createOrder(CreateOrderRequest request) {
        // 1. Create order in database
        Order order = new Order();
        order.setCustomerId(request.getCustomerId());
        order.setStatus(OrderStatus.CREATED);
        order.setTotalAmount(request.getTotalAmount());
        Order savedOrder = orderRepository.save(order);
        
        // 2. Publish event to Kafka
        OrderEvent event = new OrderEvent();
        event.setOrderId(savedOrder.getId());
        event.setCustomerId(request.getCustomerId());
        event.setTotalAmount(request.getTotalAmount());
        
        kafkaTemplate.send("order.created", event);  // ← Async!
        
        // 3. Return immediately (don't wait for payment/inventory)
        return savedOrder;  // Order created even if payment fails later!
    }
}
```

**Real Failure Scenario: SAGA Pattern in Action**

```
Scenario: Order Created, Payment Succeeds, Inventory FAILS

Timeline:
─────────────────────────────────────────────────────────

T1: Order Service
    Creates Order #100
    Publishes: OrderCreatedEvent
    ↓

T2: Payment Service (listens to order.created)
    Processes payment: $100 charged to customer
    Publishes: PaymentProcessedEvent
    ✅ Payment Success
    ↓

T3: Inventory Service (listens to order.created)
    Tries to reserve stock: 2 × Product#10
    ✋ NOT ENOUGH STOCK!
    Publishes: InventoryFailedEvent
    ❌ Inventory Failed
    ↓

T4: Payment Service (listens to inventory.failed)
    Receives: InventoryFailedEvent for Order #100
    Action: REFUND the $100
    Publishes: RefundProcessedEvent
    ✅ Refund Complete
    ↓

T5: Order Service (listens to inventory.failed)
    Receives: InventoryFailedEvent
    Updates Order #100 status: CANCELLED
    ✅ Order Cancelled

Result: Customer NOT charged! Either everything works OR everything rolls back!
```

**Code for SAGA Compensation:**
```java
@Service
public class OrderSagaOrchestrator {
    @Autowired
    private KafkaTemplate<String, Event> kafkaTemplate;
    
    @KafkaListener(topics = "inventory.failed")
    public void handleInventoryFailed(InventoryFailedEvent event) {
        Long orderId = event.getOrderId();
        
        // Step 1: Tell Payment Service to refund
        RefundEvent refundEvent = new RefundEvent();
        refundEvent.setOrderId(orderId);
        refundEvent.setAmount(event.getOrderAmount());
        refundEvent.setReason("Inventory unavailable");
        
        kafkaTemplate.send("payment.refund", refundEvent);
        
        // Step 2: Cancel the order
        orderRepository.updateOrderStatus(orderId, OrderStatus.CANCELLED);
        
        // Step 3: Log the compensation
        System.out.println("SAGA Compensation: Order " + orderId + 
                          " cancelled and refund initiated");
    }
}
```

---

### 💰 **Payment Service** (Port 8084)

**What it does:**
- Processes payments
- Validates payment methods
- Handles refunds
- Publishes payment events for order tracking

**Its Database:** PostgreSQL `payment_db`
```sql
CREATE TABLE payments (
    id BIGINT PRIMARY KEY,
    order_id BIGINT,
    customer_id BIGINT,
    amount DECIMAL(10, 2),
    status VARCHAR(50),  -- PENDING, COMPLETED, FAILED, REFUNDED
    payment_method VARCHAR(50),
    created_at TIMESTAMP,
    updated_at TIMESTAMP
);

CREATE TABLE refunds (
    id BIGINT PRIMARY KEY,
    payment_id BIGINT,
    refund_amount DECIMAL(10, 2),
    reason VARCHAR(255),
    created_at TIMESTAMP
);
```

**How it works (Event-Driven):**
```
Kafka: order.created
       ↓
   Payment Service:
   1. Receives OrderCreatedEvent
   2. Extracts: orderId, customerId, amount
   3. Charges payment gateway (Stripe/PayPal)
   4. If SUCCESS:
      - Saves to payment_db with status=COMPLETED
      - Publishes: PaymentProcessedEvent
   5. If FAILURE:
      - Saves with status=FAILED
      - Publishes: PaymentFailedEvent
      - Order Service sees failure, cancels order
```

**Real Code:**
```java
@Service
public class PaymentService {
    @Autowired
    private PaymentRepository paymentRepository;
    
    @Autowired
    private KafkaTemplate<String, PaymentEvent> kafkaTemplate;
    
    @KafkaListener(topics = "order.created")
    public void handleOrderCreated(OrderEvent orderEvent) {
        try {
            // Call payment gateway
            StripeResponse response = stripeClient.charge(
                orderEvent.getCustomerId(),
                orderEvent.getTotalAmount()
            );
            
            // Save payment record
            Payment payment = new Payment();
            payment.setOrderId(orderEvent.getOrderId());
            payment.setAmount(orderEvent.getTotalAmount());
            payment.setStatus(PaymentStatus.COMPLETED);
            paymentRepository.save(payment);
            
            // Publish success event
            PaymentEvent successEvent = new PaymentEvent();
            successEvent.setOrderId(orderEvent.getOrderId());
            successEvent.setStatus("SUCCESS");
            kafkaTemplate.send("payment.processed", successEvent);
            
        } catch (Exception e) {
            // Publish failure event
            PaymentEvent failureEvent = new PaymentEvent();
            failureEvent.setOrderId(orderEvent.getOrderId());
            failureEvent.setStatus("FAILED");
            failureEvent.setReason(e.getMessage());
            kafkaTemplate.send("payment.failed", failureEvent);
        }
    }
    
    @KafkaListener(topics = "inventory.failed")
    public void handleInventoryFailed(InventoryFailedEvent event) {
        // Compensation: Refund the payment
        Payment payment = paymentRepository.findByOrderId(event.getOrderId());
        
        stripeClient.refund(payment.getStripeChargeId(), 
                           payment.getAmount());
        
        payment.setStatus(PaymentStatus.REFUNDED);
        paymentRepository.save(payment);
        
        System.out.println("Refund processed for order: " + event.getOrderId());
    }
}
```

**Real Failure Scenario: Circuit Breaker**

```
Scenario: Payment Gateway is Down (Stripe/PayPal unavailable)

Without Circuit Breaker:
─────────────────────────

Request 1: Stripe down → Try to connect → TIMEOUT (30 seconds)
Request 2: Stripe down → Try to connect → TIMEOUT (30 seconds)
Request 3: Stripe down → Try to connect → TIMEOUT (30 seconds)
...
Result: Payment Service thread pool exhausted, becomes unresponsive!

With Circuit Breaker:
──────────────────────

Request 1: Stripe down → Call fails → Failure count = 1
Request 2: Stripe down → Call fails → Failure count = 2
Request 3: Stripe down → Call fails → Failure count = 3
Request 4: Stripe down → Call fails → Failure count = 4
Request 5: Stripe down → Call fails → Failure count = 5 ✋ THRESHOLD!

CIRCUIT BREAKER OPENS:
  ├─ Stop calling Stripe (Circuit = OPEN)
  ├─ Return default response: "Try again later"
  ├─ Payment Service stays healthy
  └─ Wait 30 seconds, then try again

After 30 seconds:
  Stripe is back up!
  Try 1 call (Circuit = HALF_OPEN)
  Success! → Circuit CLOSES
  Back to normal
```

**Code for Circuit Breaker:**
```java
@Service
public class PaymentGatewayClient {
    
    @CircuitBreaker(
        failureThreshold = 5,        // Open after 5 failures
        delay = 30000,               // Wait 30 seconds before retry
        successThreshold = 1         // 1 success to close circuit
    )
    public StripeResponse chargePayment(String customerId, BigDecimal amount) {
        // This calls Stripe
        return stripeApi.charge(customerId, amount);
    }
    
    public void chargeWithFallback(String customerId, BigDecimal amount) {
        try {
            return chargePayment(customerId, amount);
        } catch (CircuitBreakerOpenException e) {
            // Circuit is OPEN, return graceful response
            System.out.println("Payment gateway temporarily unavailable");
            return new StripeResponse("PENDING", null);  // Mark as pending
        }
    }
}
```

---

### 📦 **Inventory Service** (Port 8082)

**What it does:**
- Tracks product stock levels
- Reserves inventory for orders
- Publishes inventory events
- Handles refunds and restocking

**Its Database:** MongoDB `inventory_db` (NoSQL - different from others!)
```javascript
// MongoDB collection: products
db.products.insertOne({
    _id: ObjectId("..."),
    productId: 10,
    productName: "Laptop",
    stockLevel: 50,
    reservedQuantity: 5,
    availableQuantity: 45,
    warehouseLocation: "A-12-3",
    lastUpdated: ISODate("2024-01-15T10:30:00Z")
});

// MongoDB collection: reservations
db.reservations.insertOne({
    _id: ObjectId("..."),
    orderId: 100,
    productId: 10,
    quantity: 2,
    status: "RESERVED",  // RESERVED, RELEASED, EXPIRED
    createdAt: ISODate("2024-01-15T10:00:00Z"),
    expiresAt: ISODate("2024-01-15T11:00:00Z")  // Auto-release if not confirmed
});
```

**How it works:**
```
Kafka: order.created
       ↓
   Inventory Service:
   1. Receives OrderCreatedEvent
   2. For each item in order:
      a. Check if stock available
      b. If YES → Reserve stock, reduce available_quantity
            → Publish: InventoryReservedEvent ✅
      c. If NO → Publish: InventoryFailedEvent ❌
                 (Payment Service refunds, Order cancelled)
```

**Real Code:**
```java
@Service
public class InventoryService {
    @Autowired
    private InventoryRepository inventoryRepository;
    
    @Autowired
    private ReservationRepository reservationRepository;
    
    @Autowired
    private KafkaTemplate<String, InventoryEvent> kafkaTemplate;
    
    @KafkaListener(topics = "order.created")
    public void handleOrderCreated(OrderEvent orderEvent) {
        List<OrderItem> items = orderEvent.getItems();
        boolean allReserved = true;
        
        for (OrderItem item : items) {
            Product product = inventoryRepository.findById(item.getProductId());
            
            // Check if enough stock
            if (product.getAvailableQuantity() >= item.getQuantity()) {
                // Reserve the stock
                Reservation reservation = new Reservation();
                reservation.setOrderId(orderEvent.getOrderId());
                reservation.setProductId(item.getProductId());
                reservation.setQuantity(item.getQuantity());
                reservation.setStatus(ReservationStatus.RESERVED);
                reservation.setExpiresAt(Instant.now().plus(1, ChronoUnit.HOURS));
                
                reservationRepository.save(reservation);
                
                // Update available quantity
                product.setAvailableQuantity(
                    product.getAvailableQuantity() - item.getQuantity()
                );
                product.setReservedQuantity(
                    product.getReservedQuantity() + item.getQuantity()
                );
                inventoryRepository.save(product);
                
            } else {
                allReserved = false;
                break;  // Stop if any item cannot be reserved
            }
        }
        
        // Publish result
        if (allReserved) {
            InventoryReservedEvent successEvent = new InventoryReservedEvent();
            successEvent.setOrderId(orderEvent.getOrderId());
            successEvent.setStatus("ALL_ITEMS_RESERVED");
            kafkaTemplate.send("inventory.reserved", successEvent);
        } else {
            InventoryFailedEvent failureEvent = new InventoryFailedEvent();
            failureEvent.setOrderId(orderEvent.getOrderId());
            failureEvent.setStatus("INSUFFICIENT_STOCK");
            kafkaTemplate.send("inventory.failed", failureEvent);
        }
    }
    
    @KafkaListener(topics = "payment.failed")
    public void handlePaymentFailed(PaymentFailedEvent event) {
        // Compensation: Release the reservation
        List<Reservation> reservations = 
            reservationRepository.findByOrderId(event.getOrderId());
        
        for (Reservation res : reservations) {
            Product product = inventoryRepository.findById(res.getProductId());
            product.setAvailableQuantity(
                product.getAvailableQuantity() + res.getQuantity()
            );
            product.setReservedQuantity(
                product.getReservedQuantity() - res.getQuantity()
            );
            inventoryRepository.save(product);
            
            res.setStatus(ReservationStatus.RELEASED);
            reservationRepository.save(res);
        }
    }
}
```

**Real Failure Scenario: Auto-Expiring Reservations**

```
Scenario: Order created, inventory reserved, but payment takes too long

T0:00 - Order created
        └─→ Inventory reserved: 2 × Laptop
            Reservation expires at T1:00 (1 hour timeout)

T0:15 - Payment Service slow
        Trying to charge payment
        (Payment gateway is having issues)

T0:45 - Payment still processing
        Reservation still active
        Stock still locked

T1:00 - TIMEOUT! ✋
        Scheduler checks expired reservations
        Finds this one: expired!
        
        Automatic Compensation:
        ├─ Release the stock (2 × Laptop back to available)
        └─ Set Reservation status = EXPIRED

T1:01 - Another customer can now buy those 2 Laptops!
        Previous order gets cancelled automatically
```

---

### 🏷️ **Product Service** (Port 8085)

**What it does:**
- Manages product catalog
- Stores product details, pricing, categories
- Provides search functionality
- No events (read-only for other services)

**Its Database:** PostgreSQL `product_db`
```sql
CREATE TABLE products (
    id BIGINT PRIMARY KEY,
    name VARCHAR(255),
    description TEXT,
    price DECIMAL(10, 2),
    category VARCHAR(100),
    image_url VARCHAR(500),
    created_at TIMESTAMP,
    updated_at TIMESTAMP
);

CREATE TABLE categories (
    id BIGINT PRIMARY KEY,
    name VARCHAR(100),
    description TEXT
);
```

**How it communicates:**
```
GET http://api-gateway:8080/api/products/10
    ↓
API Gateway routes to Product Service
    ↓
Product Service queries product_db
    ↓
Returns product details (no async events needed!)
```

---

## 🌉 **API Gateway** (Port 8080)

The entry point for ALL external requests!

```
┌─────────────────────────────────────────────────────┐
│              YOUR APPLICATION CODE                  │
│         (Web Browser, Mobile App, etc.)            │
└────────────────┬────────────────────────────────────┘
                 │
                 │ ALL requests MUST go through here
                 │
                 ▼
        ┌─────────────────────┐
        │   API GATEWAY       │
        │  Port: 8080         │
        │                     │
        │  Responsibilities:  │
        │  ├─ Route requests  │
        │  ├─ Validate JWT    │
        │  ├─ Rate limiting   │
        │  └─ Load balance    │
        └────────┬────────────┘
                 │
    ┌────────────┼────────────┬───────────┬──────────┐
    │            │            │           │          │
   GET/          POST          POST        GET       GET
  /customers   /orders      /payments   /inventory  /products
    │            │            │           │          │
    ▼            ▼            ▼           ▼          ▼
 Customer     Order        Payment     Inventory   Product
 Service      Service      Service     Service     Service
 :8081        :8083        :8084       :8082       :8085
```

**Real Configuration:**
```yaml
# API Gateway application.yml
server:
  port: 8080

spring:
  application:
    name: api-gateway
  cloud:
    gateway:
      routes:
        # Route 1: Customer Service
        - id: customer-service
          uri: lb://customer-service  # Load balanced via Eureka
          predicates:
            - Path=/api/customers/**
          filters:
            - JwtAuthenticationFilter  # Validate JWT
            - RateLimitFilter           # Rate limit: 100 req/min
        
        # Route 2: Order Service
        - id: order-service
          uri: lb://order-service
          predicates:
            - Path=/api/orders/**
          filters:
            - JwtAuthenticationFilter
            - RateLimitFilter
        
        # Route 3: Payment Service
        - id: payment-service
          uri: lb://payment-service
          predicates:
            - Path=/api/payments/**
          filters:
            - JwtAuthenticationFilter
            - RateLimitFilter
        
        # Route 4: Inventory Service
        - id: inventory-service
          uri: lb://inventory-service
          predicates:
            - Path=/api/inventory/**
          filters:
            - JwtAuthenticationFilter
        
        # Route 5: Product Service
        - id: product-service
          uri: lb://product-service
          predicates:
            - Path=/api/products/**
          # No authentication needed for public products
```

---

## 🔄 **Eureka Service Discovery** (Port 8761)

How services find each other dynamically!

```
SERVICE STARTUP:
────────────────

Order Service starts
    ↓
  Registers with Eureka:
  "Hi Eureka! I'm Order Service"
  "Running at: http://order-service:8083"
  "Health check URL: /actuator/health"
    ↓
Eureka adds to registry

Payment Service starts
    ↓
  Registers with Eureka:
  "Hi Eureka! I'm Payment Service"
    ↓
Eureka now knows about both!


SERVICE DISCOVERY:
──────────────────

Payment Service needs to call Order Service
    ↓
  Asks Eureka: "Where is Order Service?"
    ↓
Eureka responds: "Order Service is at http://order-service:8083"
    ↓
Payment Service connects!


HEALTH CHECKS:
──────────────

Every 30 seconds, Eureka pings each service:
    ↓
  GET http://order-service:8083/actuator/health
    ↓
If UP (✅): Keep in registry
If DOWN (❌): Remove from registry (mark as out of service)

Result: If Order Service crashes, Eureka removes it automatically!
        Other services know not to call it.
```

**Real Configuration in Each Service:**
```yaml
# Every microservice has this in application.yml

eureka:
  client:
    register-with-eureka: true      # Register yourself
    fetch-registry: true             # Download registry from Eureka
    serviceUrl:
      defaultZone: http://discovery-server:8761/eureka/
    initialInstanceInfoReplicationIntervalSeconds: 40
    instanceInfoReplicationIntervalSeconds: 30
  
  instance:
    leaseRenewalIntervalInSeconds: 30    # Heartbeat every 30 sec
    leaseExpirationDurationInSeconds: 90 # Remove if no heartbeat for 90 sec
    prefer-ip-address: false
    instance-id: ${spring.application.name}:${spring.instance_id:${random.value}}
    
    health-check-url: http://${eureka.instance.hostname}:${server.port}/actuator/health
    metadata-map:
      management:
        endpoints:
          web:
            exposure:
              include: health,info,metrics
```

---

## ⚙️ **Config Server** (Port 8888)

Central configuration management for all services!

```
┌──────────────────────────┐
│    CONFIG SERVER         │
│   Port: 8888             │
│                          │
│  Git Repository:         │
│  ├─ config/              │
│  │  ├─ application.yml   │
│  │  ├─ order-service.yml │
│  │  ├─ payment-service.yml
│  │  ├─ inventory-service.yml
│  │  └─ customer-service.yml
└──────────────────────────┘
          △ │
          │ │ Pull config
          │ │ (on startup)
          │ │
    ┌─────┘ │
    │       │
Order│      Payment
Svc  │      Svc
     │
Inventory
Svc

Without Config Server (❌ BAD):
────────────────────────────────
Database URL hardcoded in Order Service JAR
Database URL changes: 192.168.1.10:5432 → 192.168.2.20:5432
To update: Rebuild JAR → Redeploy Order Service → Restart

Time wasted: 30 minutes! 😞

With Config Server (✅ GOOD):
──────────────────────────────
Database URL in Git config file
Database URL changes: 192.168.1.10:5432 → 192.168.2.20:5432
Config Server fetches new config from Git
All services auto-refresh configuration
No rebuild, no redeploy needed!

Time saved: 2 minutes! 🚀
```

---

## 🎯 Complete Order Flow Example

Let me show you HOW ALL THE PIECES WORK TOGETHER:

```
CUSTOMER PLACES ORDER:
══════════════════════

T1: Client
    POST /api/orders (via API Gateway)
    {
      "customerId": 5,
      "items": [{"productId": 10, "quantity": 2}]
    }
    ↓

T2: API Gateway
    ├─ Validates JWT token ✅
    ├─ Rate limit check ✅ (100 req/min)
    └─ Routes to Order Service
       ↓

T3: Order Service
    1. Receives request
    2. Creates order in DB (status = CREATED)
    3. Publishes: "OrderCreatedEvent" to Kafka topic "order.created"
    4. Returns: { "orderId": 123, "status": "CREATED" } (IMMEDIATELY!)
    ↓
    
T4: Kafka Queue
    "order.created" event sits in queue
    Multiple subscribers listening...
       ↓ ├─→ Payment Service (subscriber 1)
         └─→ Inventory Service (subscriber 2)


PARALLEL PROCESSING:
════════════════════

Payment Service (async)              Inventory Service (async)
────────────────────────────────     ───────────────────────────

T5: Receives OrderCreatedEvent       T5: Receives OrderCreatedEvent
    Calls Stripe API                     Checks stock levels
    Charges $100                         Product 10: 50 in stock
    Payment SUCCESS ✅                   Reserve 2 units
    Publishes: PaymentProcessedEvent     SUCCESS ✅
    ↓                                    Publishes: InventoryReservedEvent
                                         ↓

T6: Kafka topics:
    - payment.processed ✅
    - inventory.reserved ✅
    
    Order Service listens to BOTH:
    ├─ Sees payment.processed ✅
    └─ Sees inventory.reserved ✅
    
    Updates order_db:
    Order 123 status = PAID + STOCK_RESERVED
    ↓

T7: Return to Customer:
    Order 123 successfully created!
    Status: PAID + STOCK_RESERVED
    Expected delivery: 2024-01-18


WHAT IF PAYMENT FAILED? (Alternative scenario)
═══════════════════════════════════════════════

T5: Payment Service
    Calls Stripe API
    ❌ DECLINED (invalid card)
    Publishes: PaymentFailedEvent
    ↓

T6: Inventory Service listens to payment.failed
    Releases the reservation (gives stock back)
    Product 10: Back to 50 units
    ↓

T7: Order Service listens to payment.failed
    Updates order_db:
    Order 123 status = FAILED
    ↓

T8: Return to Customer:
    ❌ Order failed
    Reason: Payment declined
    Please try again with different card
```

---

## 📊 Communication Methods In Your Project

### **Synchronous (Direct Service Calls)**
Used for: Getting data that's needed RIGHT NOW

```java
// Example: API Gateway needs to validate customer exists

@RestController
@RequestMapping("/api/orders")
public class OrderController {
    
    @Autowired
    private RestTemplate restTemplate;  // HTTP client
    
    @PostMapping
    public ResponseEntity<?> createOrder(@RequestBody CreateOrderRequest req) {
        // SYNCHRONOUS: Call Customer Service directly
        // Wait for response before continuing
        try {
            ResponseEntity<Customer> resp = restTemplate.getForEntity(
                "http://customer-service:8081/api/customers/" + req.getCustomerId(),
                Customer.class
            );
            
            if (!resp.getStatusCode().is2xxSuccessful()) {
                return ResponseEntity.status(400).body("Customer not found");
            }
            
            // Only if customer exists, create order
            Order order = new Order();
            order.setCustomerId(req.getCustomerId());
            return ResponseEntity.ok(order);
            
        } catch (RestClientException e) {
            return ResponseEntity.status(503).body("Service unavailable");
        }
    }
}

// Problem: If Customer Service is slow, Order API is slow!
// Solution: Use Circuit Breaker pattern (already shown)
```

### **Asynchronous (Event-Based)**
Used for: Actions that don't need immediate response

```java
// Example: Order created → Notify Payment & Inventory asynchronously

@Service
public class OrderService {
    
    @Autowired
    private KafkaTemplate<String, OrderEvent> kafkaTemplate;
    
    public Order createOrder(CreateOrderRequest request) {
        // 1. Save order (FAST)
        Order order = new Order();
        order.setCustomerId(request.getCustomerId());
        Order savedOrder = orderRepository.save(order);
        
        // 2. Publish event to Kafka (FIRE AND FORGET)
        OrderEvent event = new OrderEvent();
        event.setOrderId(savedOrder.getId());
        kafkaTemplate.send("order.created", event);  // ← Don't wait!
        
        // 3. Return immediately
        return savedOrder;  // Return in < 50ms!
        // Payment & Inventory process in background
    }
}

// Benefits:
// - Order API returns IMMEDIATELY (< 50ms)
// - Payment & Inventory process in parallel
// - If Payment Service is slow, Order API not affected
// - If Inventory crashes, Order still created
```

---

## ⚠️ Real Problems You Might Face

### **Problem 1: Service Can't Find Discovery Server**

```
Error in logs:
────────────────
Error contacting Eureka server: http://discovery-server:8761/eureka/
Connection refused: connect

Why:
────
1. Discovery Server not started yet
2. Discovery Server crashed
3. Network issue between services

Solution:
──────────
# Check if Discovery Server is running
docker-compose ps | grep discovery-server

# If not running, start it
docker-compose up -d discovery-server

# Wait 30 seconds for it to start
sleep 30

# Now start other services
docker-compose up -d order-service payment-service
```

### **Problem 2: Kafka Connection Refused**

```
Error in logs:
────────────────
org.apache.kafka.common.errors.TimeoutException: 
Timed out connecting to bootstrap servers: kafka:29092

Why:
────
1. Kafka not started
2. Kafka still starting (takes 10-15 seconds)
3. Service trying to connect before Kafka ready

Solution:
──────────
# Check Kafka logs
docker-compose logs kafka | tail -20

# Make sure Kafka fully started
docker-compose logs kafka | grep "Cluster ID"

# If not started, wait
sleep 15

# Check connectivity from Order Service
docker-compose exec order-service \
  kafka-console-consumer --bootstrap-servers kafka:29092 --list-topics
```

### **Problem 3: Service Can't Reach Database**

```
Error in logs:
────────────────
SQLSTATE [08001]: Unable to connect to server: 
Connection refused at postgresql://postgres:5432

Why:
────
1. PostgreSQL not running
2. Service using wrong hostname
3. Database password wrong

Solution:
──────────
# Check database is running
docker-compose ps | grep postgres

# View logs
docker-compose logs postgres | tail -20

# Check connectivity
docker-compose exec order-service \
  psql -h postgres -U postgres -d order_db -c "SELECT 1"
```

---

## 🚀 How to Debug Your System

### **Check All Services Status**
```bash
# List all running containers
docker-compose ps

# Example output:
# NAME                    STATUS      PORTS
# api-gateway            Up 5 min    0.0.0.0:8080->8080/tcp
# order-service          Up 4 min    0.0.0.0:8083->8083/tcp
# payment-service        Up 4 min    0.0.0.0:8084->8084/tcp
# inventory-service      Up 4 min    0.0.0.0:8082->8082/tcp
# customer-service       Up 4 min    0.0.0.0:8081->8081/tcp
# product-service        Up 4 min    0.0.0.0:8085->8085/tcp
# discovery-server       Up 6 min    0.0.0.0:8761->8761/tcp
# config-server          Up 6 min    0.0.0.0:8888->8888/tcp
# kafka                  Up 5 min    0.0.0.0:9092->9092/tcp
# postgres               Up 6 min    5432/tcp
```

### **Check Service Health**
```bash
# Check if Order Service is healthy
curl http://localhost:8083/actuator/health

# Example response:
# {
#   "status": "UP",
#   "components": {
#     "db": {"status": "UP"},
#     "diskSpace": {"status": "UP"},
#     "kafka": {"status": "UP"},
#     "eureka": {"status": "UP"}
#   }
# }
```

### **Check Eureka Registry**
```bash
# See which services are registered
curl http://localhost:8761/eureka/apps

# Example: Order Service registered?
curl http://localhost:8761/eureka/apps/order-service

# If not registered, it's probably crashed or config issue
```

### **Check Kafka Messages**
```bash
# List all topics
docker-compose exec kafka \
  kafka-topics --bootstrap-servers localhost:9092 --list

# Read messages from a topic
docker-compose exec kafka \
  kafka-console-consumer --bootstrap-servers localhost:9092 \
  --topic order.created --from-beginning --max-messages 5
```

---

## ✅ Key Takeaways

1. **Your 5 microservices** each have one job:
   - Customer: User accounts
   - Order: Order creation & tracking
   - Payment: Charge cards
   - Inventory: Stock management
   - Product: Product catalog

2. **Databases are separate**:
   - Customer → PostgreSQL
   - Order → PostgreSQL
   - Payment → PostgreSQL
   - Inventory → MongoDB (Different DB type!)
   - Product → PostgreSQL

3. **Communication is ASYNC via Kafka**:
   - Order Service publishes "OrderCreatedEvent"
   - Payment Service subscribes & processes payment
   - Inventory Service subscribes & reserves stock
   - All happens in parallel! ⚡

4. **Eureka helps services find each other**:
   - Services register when they start
   - Other services ask Eureka "Where is Payment Service?"
   - Eureka responds with the URL
   - No hardcoding addresses! ✅

5. **API Gateway is the entry point**:
   - All external requests go through API Gateway
   - API Gateway routes to the right service
   - Security (JWT), rate limiting, load balancing done here

6. **Config Server centralizes configuration**:
   - Database URLs, API keys, etc. in Git
   - No need to rebuild JAR when config changes
   - Services auto-refresh configuration

---

## 📝 Next Steps

Now that you understand how your project works, move to:
- **MICROSERVICES_VISUAL_GUIDE.md** - See ASCII diagrams of your architecture
- **MICROSERVICES_ZERO_TO_HERO.md** - Deep dive into patterns and production practices

---

**Remember:** Your micro-eCommerce is a textbook example of microservices done right! 🎯
