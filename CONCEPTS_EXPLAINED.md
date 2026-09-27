# Advanced Microservices Concepts Explained

## 📚 Deep Dive Into Key Technical Concepts

This guide explains the **why** and **how** of critical patterns used throughout the project.

---

## 1. Event Sourcing vs Traditional CRUD

### Traditional Approach
```
DELETE → Read Customer
         Customer: {id: 1, email: "john@example.com", status: "INACTIVE"}

Problem: Lost information!
- When did status change?
- Why was it deactivated?
- Who did it?
- What was the old email?
```

### Event Sourcing Approach
```
Event Stream (Immutable):
1. CustomerCreatedEvent(id: 1, email: "john@example.com")
2. CustomerUpdatedEvent(id: 1, email: "john.doe@example.com")
3. CustomerStatusChangedEvent(id: 1, status: "PREMIUM")
4. CustomerDeactivatedEvent(id: 1, reason: "Inactive for 6 months")

Current State (Derived):
Customer: {
  id: 1,
  email: "john.doe@example.com",
  status: "INACTIVE",
  history: [3 events showing progression]
}

Benefits:
✓ Complete audit trail
✓ Can replay to any point in time
✓ Know exactly what changed and when
✓ Enables temporal queries
```

### When to Use Event Sourcing
- **Financial transactions** (must be auditable)
- **Healthcare records** (compliance requirements)
- **Order systems** (need complete history)
- **User activity tracking** (understand behavior)

### Implementation in Project
```java
// All orders published as events
orderRepository.save(order);                    // Persist order
eventPublisher.publishEvent(                    // Publish event
    new OrderCreatedEvent(order)
);

// Event stored in Kafka topics
kafka-topics:
- order.created
- order.confirmed
- order.cancelled
- order.shipped

// Can reconstruct complete order history
List<DomainEvent> history = eventStore.findByAggregateId(orderId);
Order reconstructedOrder = Order.fromEvents(history);
```

---

## 2. Saga Pattern for Distributed Transactions

### Problem: ACID Transactions Don't Work Across Services
```
Traditional Database Transaction:
BEGIN
  - Update Order Service DB
  - Update Payment Service DB
  - Update Inventory Service DB
COMMIT (all succeed) or ROLLBACK (all fail)

Microservices Reality:
- Order Service and Payment Service are different databases
- No distributed lock coordinator
- Network failures possible mid-transaction
- Can't have true ACID across services
```

### Solution: Saga Pattern

#### Choreography (Event-Driven)
```
Order Service
  └─ Create Order
     └─ Publish: OrderCreatedEvent
        ↓
Payment Service
  ├─ Receive: OrderCreatedEvent
  ├─ Process Payment
  ├─ On Success: Publish: PaymentProcessedEvent
  └─ On Failure: Publish: PaymentFailedEvent
     ↓
Inventory Service
  ├─ Receive: OrderCreatedEvent
  ├─ Receive: PaymentProcessedEvent (if payment succeeded)
  ├─ Reserve Inventory
  └─ Publish: InventoryReservedEvent
     ↓
Order Service
  ├─ Receive: InventoryReservedEvent
  ├─ Update Order Status to CONFIRMED
  └─ Publish: OrderConfirmedEvent

On Failure (e.g., Payment fails):
Payment Service
  └─ Publish: PaymentFailedEvent
     ↓
Inventory Service
  └─ Receive: PaymentFailedEvent
     └─ Release any reservations (compensation)
```

#### Orchestration (Centralized)
```
Order Saga Orchestrator (State Machine)
├─ Step 1: CreateOrder
│  ├─ Call OrderService.createOrder()
│  └─ On success: Go to Step 2
│
├─ Step 2: ProcessPayment
│  ├─ Call PaymentService.processPayment()
│  ├─ On success: Go to Step 3
│  └─ On failure: Go to Compensate Step 1
│
├─ Step 3: ReserveInventory
│  ├─ Call InventoryService.reserve()
│  ├─ On success: Go to Confirm
│  └─ On failure: Go to Compensate Steps 1-2
│
└─ Compensate (Rollback):
   ├─ Step 1: Release Payment
   ├─ Step 2: Cancel Order
   └─ Return error to client
```

### Implementation Example
```java
@Service
public class OrderSagaOrchestrator {
    
    // Compensating transactions
    private void compensatePayment(Order order) {
        paymentService.refund(order.getPaymentId());
    }
    
    private void compensateInventory(Order order) {
        inventoryService.releaseReservation(order.getId());
    }
    
    // Execute saga
    public Order executeOrderSaga(CreateOrderRequest request) {
        try {
            // Step 1: Create order
            Order order = orderService.createOrder(request);
            
            // Step 2: Process payment
            PaymentResult payment = paymentService.processPayment(
                order.getId(),
                order.getTotalAmount()
            );
            if (!payment.isSuccess()) {
                compensatePayment(order);
                throw new PaymentFailedException();
            }
            
            // Step 3: Reserve inventory
            InventoryResult inventory = inventoryService.reserveItems(
                order.getItems()
            );
            if (!inventory.isSuccess()) {
                compensatePayment(order);
                compensateInventory(order);
                throw new InventoryFailedException();
            }
            
            // All steps succeeded
            order.setStatus(OrderStatus.CONFIRMED);
            return order;
            
        } catch (Exception e) {
            // Compensation already handled
            throw e;
        }
    }
}
```

---

## 3. Circuit Breaker Pattern

### Why It Matters
```
Without Circuit Breaker:
Request arrives
  ↓
Call PaymentService (DOWN)
  └─ Wait for timeout (30 seconds)
     └─ Return error
     └─ Request another (another 30 seconds)
     └─ Another request...

Result: Cascading failures, wasted resources!

With Circuit Breaker:
Request arrives
  ↓
Check circuit breaker state
  ├─ CLOSED: Call service normally
  ├─ OPEN: Return fallback immediately (no call!)
  └─ HALF_OPEN: Allow test call
```

### States & Transitions
```
        CLOSED (Normal)
            ↓
        Count failures
            ↓
    Failure rate > threshold?
         ↙         ↘
       NO          YES
        ↓           ↓
      CLOSED → OPEN
                 ↓
          Return fallback
                 ↓
            Wait X seconds
                 ↓
             HALF_OPEN
                 ↓
          Allow test request
             ↙         ↘
      Success        Failure
        ↙              ↘
     CLOSED          OPEN
```

### Configuration
```yaml
resilience4j:
  circuitbreaker:
    instances:
      paymentService:
        slidingWindowSize: 5          # Check last 5 requests
        failureRateThreshold: 50      # 50% failure = open
        slowCallRateThreshold: 50     # 50% slow = open
        slowCallDurationThreshold: 2s # Slower than 2s = slow
        waitDurationInOpenState: 10s  # Wait 10s before trying again
        permittedNumberOfCallsInHalfOpenState: 2
```

### Code Example
```java
@Service
public class PaymentService {
    
    @CircuitBreaker(
        name = "paymentService",
        fallbackMethod = "processPaymentFallback"
    )
    public PaymentResult processPayment(PaymentRequest request) {
        // This call is protected by circuit breaker
        return paymentGateway.charge(request);
    }
    
    // Fallback: called when circuit is OPEN
    public PaymentResult processPaymentFallback(
        PaymentRequest request,
        Exception e
    ) {
        logger.warn("Payment service down, using fallback", e);
        return PaymentResult.builder()
            .success(false)
            .message("Payment service temporarily unavailable")
            .build();
    }
}
```

---

## 4. Rate Limiting with Token Bucket

### Algorithm
```
Bucket Configuration:
- Capacity: 1000 tokens
- Refill Rate: 1000 tokens per minute (16.67 tokens/second)

Request Processing:
1. Request arrives
2. Check bucket: current = 950 tokens
3. Cost = 1 token
4. If current >= cost:
   - Remove 1 token (current = 949)
   - Allow request ✓
5. Else:
   - Return 429 Too Many Requests

Over Time:
- Tokens automatically refilled at rate
- Burst requests allowed up to capacity
- Smooth out traffic spikes
```

### Why Token Bucket?
```
vs Fixed Window (Simple):
┌──────────┬──────────┬──────────┐
│ Minute 1 │ Minute 2 │ Minute 3 │
│ 1000 req │ 1000 req │ 1000 req │
└──────────┴──────────┴──────────┘
Problem: Large spike between windows

Token Bucket:
- Continuous refill
- Burst capacity
- Smooth distribution
```

### Implementation
```java
@Configuration
public class RateLimitingConfig {
    
    @Bean
    public RateLimiter rateLimiter() {
        // Create rate limiter: 1000 requests per minute
        return RateLimiter.create(
            1000.0 / 60  // 16.67 permits per second
        );
    }
}

@Component
public class RateLimitingFilter extends OncePerRequestFilter {
    
    @Override
    protected void doFilterInternal(
        HttpServletRequest request,
        HttpServletResponse response,
        FilterChain filterChain
    ) throws ServletException, IOException {
        
        String clientId = extractClientId(request);
        RateLimiter limiter = rateLimiters.get(clientId);
        
        if (!limiter.tryAcquire()) {
            // No token available
            response.setStatus(429);  // Too Many Requests
            response.getWriter().write("Rate limit exceeded");
            return;
        }
        
        // Token consumed, proceed
        filterChain.doFilter(request, response);
    }
}
```

---

## 5. Distributed Tracing with Correlation IDs

### Problem: Finding Issues in Distributed System
```
Client Request
  ↓
API Gateway (takes 100ms)
  ├─ Calls Order Service (takes 200ms)
  │  ├─ Calls Inventory Service (takes 300ms)
  │  └─ Calls Payment Service (takes 400ms)
  └─ Returns response
  
Total: 1 second

Where's the bottleneck? Need to trace across services!
```

### Solution: Correlation ID
```
Request arrives at Gateway
  └─ Generate or extract X-Trace-ID: "trace-abc123"
     └─ Log: [trace-abc123] Gateway received request
        └─ Forward header to Order Service
           └─ Log: [trace-abc123] OrderService processing
              └─ Call Inventory Service with header
                 └─ Log: [trace-abc123] InventoryService processing
              └─ Call Payment Service with header
                 └─ Log: [trace-abc123] PaymentService processing

All logs tagged with same trace ID!

Analysis:
grep "trace-abc123" application.log
  [trace-abc123] Gateway: request received (00:00:00.000)
  [trace-abc123] OrderService: processing (00:00:00.100)
  [trace-abc123] InventoryService: checking stock (00:00:00.150)
  [trace-abc123] PaymentService: processing (00:00:00.200)
  [trace-abc123] Gateway: response sent (00:00:01.000)
```

### Implementation
```java
@Component
public class RequestResponseLoggingFilter extends OncePerRequestFilter {
    
    @Override
    protected void doFilterInternal(
        HttpServletRequest request,
        HttpServletResponse response,
        FilterChain filterChain
    ) throws ServletException, IOException {
        
        // Extract trace ID from header or generate new one
        String traceId = request.getHeader("X-Trace-ID");
        if (traceId == null) {
            traceId = UUID.randomUUID().toString();
        }
        
        // Add to MDC (Mapped Diagnostic Context)
        // All logs from this thread will include this value
        MDC.put("traceId", traceId);
        
        try {
            long startTime = System.currentTimeMillis();
            
            // Add header to response and downstream calls
            response.addHeader("X-Trace-ID", traceId);
            
            logger.info("Request: {} {}", request.getMethod(), request.getRequestURI());
            
            filterChain.doFilter(request, response);
            
            long duration = System.currentTimeMillis() - startTime;
            logger.info(
                "Response: {} {} ({}ms)",
                request.getMethod(),
                request.getRequestURI(),
                duration
            );
        } finally {
            MDC.remove("traceId");
        }
    }
}

// In logback-spring.xml:
// Pattern includes %X{traceId}
// Output: [trace-abc123] Request: GET /api/customers
```

---

## 6. Cache Invalidation Strategies

### The Hard Problem
```
"There are only two hard things in Computer Science:
 cache invalidation and naming things."
 — Phil Karlton
```

### Three Strategies

#### 1. TTL (Time To Live)
```
┌────────────────────────────┐
│ Cache Entry                │
├────────────────────────────┤
│ Key: customer:123          │
│ Value: Customer{...}       │
│ TTL: 600 seconds (10 min)  │
└────────────────────────────┘

Timeline:
T=0:     Entry created
T=300s:  Still valid
T=600s:  Expired, removed
T=601s:  Cache miss, fetch from DB

Good for: Less critical data with acceptable staleness
```

#### 2. Event-Driven Invalidation
```
Customer Profile Updated
  ↓
Publish CustomerUpdatedEvent
  ↓
@CacheEvict(value = "customers", key = "#id")
  ├─ Remove from cache
  └─ Next request fetches fresh data from DB

Good for: Critical data needing immediate updates
```

#### 3. Refresh on Write
```
Update operation:
1. Update in database
2. Update in cache immediately
3. Return success

Good for: High-read scenarios
Problem: Cache divergence if update fails after DB write
```

### Best Practice
```java
@Service
public class CustomerService {
    
    // Retrieve: Return from cache if available
    @Cacheable(value = "customers", key = "#id")
    public Customer getCustomer(Long id) {
        return customerRepository.findById(id);
    }
    
    // Update: Invalidate cache
    @CacheEvict(value = "customers", key = "#id")
    public Customer updateCustomer(Long id, CustomerDTO dto) {
        Customer customer = customerRepository.findById(id);
        customer.update(dto);
        return customerRepository.save(customer);
    }
    
    // Delete: Invalidate cache
    @CacheEvict(value = "customers", key = "#id")
    public void deleteCustomer(Long id) {
        customerRepository.deleteById(id);
    }
}
```

---

## 7. Idempotency in Distributed Systems

### Problem: Duplicate Requests
```
Client sends request
  ↓
Server processes, updates DB
  ↓
Network timeout (client doesn't receive response)
  ↓
Client retries same request
  ↓
Server processes AGAIN!
  └─ Database now has duplicate transactions
```

### Solution: Idempotency Keys
```
Client sends request with unique ID:
POST /api/orders
X-Idempotency-Key: order-uuid-12345
{...}

Server logic:
1. Check if request already processed
2. If yes: return cached response
3. If no: process and cache response

On retry:
POST /api/orders
X-Idempotency-Key: order-uuid-12345
  └─ Recognized as duplicate
  └─ Return previous response immediately
  └─ No duplicate order created!
```

### Implementation
```java
@Service
public class OrderService {
    
    private Map<String, OrderResponse> idempotencyCache = new ConcurrentHashMap<>();
    
    public OrderResponse createOrder(
        CreateOrderRequest request,
        String idempotencyKey
    ) {
        // Check cache first
        if (idempotencyCache.containsKey(idempotencyKey)) {
            logger.warn("Duplicate request detected: {}", idempotencyKey);
            return idempotencyCache.get(idempotencyKey);
        }
        
        // Process new request
        Order order = new Order(request);
        order = orderRepository.save(order);
        
        OrderResponse response = new OrderResponse(order);
        
        // Cache response for future retries
        idempotencyCache.put(idempotencyKey, response);
        
        return response;
    }
}
```

---

## 8. Understanding CAP Theorem

### Triangle of Trade-offs
```
     Consistency
      /        \
     /          \
    C ──────────── P
     \          /
      \        /
     Availability

Pick 2 out of 3:
- Consistent (all nodes see same data)
- Available (always responsive)
- Partition tolerant (works despite network failures)
```

### Apply to Project
```
Microservices are Partition Tolerant by design
  └─ Must handle network failures
     └─ Must choose: Consistency OR Availability

Choice: Availability + Partition Tolerance (AP)
  └─ Services continue working despite network issues
  └─ Data eventually consistent
  └─ Temporary inconsistency acceptable
  
Example:
- Order created (Order Service)
- Payment processed (Payment Service)
- But Order Service doesn't know payment status immediately
- Event arrives asynchronously
- Order status updated (eventually consistent)

vs Strong Consistency would require:
- Synchronous call to Payment Service
- Wait for response
- If network fails: Order creation fails
- User sees error (bad experience)
```

---

## 9. Backpressure & Flow Control

### Problem: Producer Too Fast
```
Order Service produces events faster than
Inventory Service can consume
  └─ Queue fills up
  └─ Memory exhausted
  └─ System crash!
```

### Solution: Backpressure
```
Kafka handles backpressure:
- Producer attempts to write
- Broker checks: buffer full?
- If full: Block producer
- Producer waits
- Consumer catches up
- Buffer drains
- Producer can write again

Result: Balanced flow
```

### Configuration
```yaml
kafka:
  producer:
    batch-size: 16384              # 16KB batches
    linger-ms: 10                  # Wait 10ms for batch
    buffer-memory: 33554432        # 32MB buffer
    compression-type: snappy       # Compress for network
```

---

## 10. Consensus in Distributed Systems

### Problem: Split Brain
```
Service A thinks Service B is down
Service B thinks Service A is down
Both think they're the "primary"
  └─ Two different decisions made
  └─ Data inconsistency!
```

### Solution: Quorum-Based Consensus
```
3 nodes:
- Master writes data
- Waits for 2 out of 3 acknowledgments (quorum)
- If master fails, remaining nodes can elect new master
- Single node down: system still works
- Two nodes down: system stops (safer than split-brain)

Kubernetes uses etcd:
- Stores cluster state
- Ensures only one scheduler
- Prevents conflicting deployments
```

---

## Summary Table

| Concept | Problem | Solution | When to Use |
|---------|---------|----------|------------|
| Event Sourcing | Lost audit trail | Immutable event log | Financial, compliance |
| Saga Pattern | No distributed transactions | Choreography/orchestration | Multi-service workflows |
| Circuit Breaker | Cascading failures | Fail fast with fallbacks | External service calls |
| Rate Limiting | Overload | Token bucket | API protection |
| Distributed Tracing | Hard to debug | Correlation IDs | Production support |
| Cache Invalidation | Stale data | TTL + events | Performance |
| Idempotency | Duplicate requests | Idempotency keys | Retries |
| CAP Theorem | Can't have all 3 | Choose 2 (usually AP) | System design |
| Backpressure | Queue overflow | Flow control | Producer/consumer |
| Consensus | Split brain | Quorum | Distributed coordination |

