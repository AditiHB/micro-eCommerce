# Feign Client in Microservices 🐦

A declarative HTTP client for building REST clients with ease in microservices architecture.

---

## What is Feign Client?

Feign is a declarative web service client that simplifies HTTP communication between microservices.

### Simple Definition:
Instead of writing HTTP calls manually:
```java
// ❌ WITHOUT Feign - Manual HTTP calls
RestTemplate restTemplate = new RestTemplate();
String url = "http://payment-service/api/payments/" + orderId;
Payment payment = restTemplate.getForObject(url, Payment.class);
```

Use Feign:
```java
// ✅ WITH Feign - Declarative interface
@FeignClient("payment-service")
public interface PaymentClient {
    @GetMapping("/api/payments/{orderId}")
    Payment getPayment(@PathVariable String orderId);
}

// Use it
paymentClient.getPayment(orderId);
```

**Feign automatically:**
- Handles HTTP connection
- Converts JSON ↔ Java objects
- Builds the URL
- Sends headers
- Manages retries (with Hystrix/Resilience4j)

---

## Key Features

| Feature | Description |
|---------|-------------|
| **Declarative** | Define interface, Feign handles the rest |
| **Easy to Use** | Looks like calling a Java method |
| **Spring Integration** | Works seamlessly with Spring Boot |
| **Automatic Serialization** | JSON ↔ Java object conversion |
| **Error Handling** | Built-in fallback support |
| **Load Balancing** | Integrates with Ribbon/Spring Cloud LoadBalancer |
| **Service Discovery** | Works with Eureka, Consul, etc. |

---

## When to Use Feign Client

### ✅ Use Feign When:

**1. Microservice-to-Microservice Communication**
```java
// Order Service calls Payment Service
@FeignClient("payment-service")
public interface PaymentClient {
    @PostMapping("/charge")
    PaymentResponse chargeCard(@RequestBody ChargeRequest request);
}
```

**2. Calling External APIs**
```java
// Call third-party payment gateway
@FeignClient(name = "stripe", url = "https://api.stripe.com")
public interface StripeClient {
    @PostMapping("/v1/charges")
    ChargeResponse charge(@RequestBody ChargeRequest request);
}
```

**3. Internal Service Calls (Simple REST)**
```java
// Inventory Service calls Product Service
@FeignClient("product-service")
public interface ProductClient {
    @GetMapping("/products/{id}")
    Product getProduct(@PathVariable String id);
}
```

**4. When You Need Declarative Style**
```java
// Clean, readable, maintainable
@FeignClient("user-service")
public interface UserClient {
    @GetMapping("/users/{id}")
    User getUserById(@PathVariable String id);
    
    @PostMapping("/users")
    User createUser(@RequestBody UserRequest request);
    
    @DeleteMapping("/users/{id}")
    void deleteUser(@PathVariable String id);
}
```

---

### ❌ Don't Use Feign When:

1. **Very Simple Calls** - Use RestTemplate directly
2. **Async/Reactive Needed** - Use WebClient instead
3. **Binary/File Transfer** - Use RestTemplate
4. **High-Performance Critical** - Use WebClient (async)

---

## Fallback in Feign vs Circuit Breaker

### Fallback in Feign Client

**What it does:** Provides an alternative response when the remote service fails

```java
// 1. Create Fallback Implementation
@Component
public class PaymentClientFallback implements PaymentClient {
    @Override
    public PaymentResponse chargeCard(ChargeRequest request) {
        // Return default/cached response
        PaymentResponse response = new PaymentResponse();
        response.setStatus("PENDING");  // Retry later
        response.setTransactionId("OFFLINE-" + System.currentTimeMillis());
        return response;
    }
}

// 2. Link Fallback to Feign Client
@FeignClient(
    name = "payment-service",
    fallback = PaymentClientFallback.class
)
public interface PaymentClient {
    @PostMapping("/charge")
    PaymentResponse chargeCard(@RequestBody ChargeRequest request);
}
```

**Use Case:**
```
User tries to pay
  ↓
Call Payment Service
  ↓
Service DOWN? ❌
  ↓
Fallback returns PENDING status
  ↓
User sees "Payment is processing"
  ↓
Retry automatically later
```

---

### Fallback in Circuit Breaker (Hystrix/Resilience4j)

**What it does:** Stops calling failing service and returns fallback immediately

```java
// 1. Add Circuit Breaker annotation
@GetMapping("/orders/{id}")
@CircuitBreaker(name = "orderService", fallbackMethod = "getOrderFallback")
public Order getOrder(@PathVariable String id) {
    return orderClient.getOrder(id);
}

// 2. Fallback method
public Order getOrderFallback(String id, Exception e) {
    // Return cached or default order
    Order order = new Order();
    order.setId(id);
    order.setStatus("CACHED");
    order.setNote("Service unavailable, showing cached data");
    return order;
}
```

**Use Case:**
```
Call to Payment Service fails
  ↓
Circuit Breaker OPENS (stops sending requests)
  ↓
Later calls return fallback immediately (no network delay)
  ↓
After timeout, Circuit Breaker HALF-OPEN (tries again)
  ↓
Service recovered? CLOSE circuit (resume normal)
```

---

## Feign Fallback vs Circuit Breaker Fallback

| Aspect | Feign Fallback | Circuit Breaker Fallback |
|--------|---|---|
| **Trigger** | Every request fails | Service fails repeatedly |
| **Behavior** | Tries to call each time | Stops trying after threshold |
| **Performance** | May have network delay | Returns fallback immediately |
| **Use Case** | Graceful degradation | Prevent cascading failures |
| **State** | Stateless | Stateful (Open/Half-Open/Closed) |
| **Best For** | Occasional failures | Persistent failures |
| **Response Time** | Slower (waits for timeout) | Faster (no network attempt) |

---

## Visual Comparison

### Feign Fallback Flow
```
Request
  ↓
Try Feign Call → SUCCESS? ✅ Return Response
  ↓
Try Feign Call → FAIL? ❌ Call Fallback
  ↓
Return Fallback Response
  ↓
(Next request tries again)
```

### Circuit Breaker Fallback Flow
```
Request #1
  ├─ Try Feign Call → FAIL ❌
  └─ Fallback #1 (return)

Request #2
  ├─ Try Feign Call → FAIL ❌
  └─ Fallback #2 (return)

Request #3
  ├─ Try Feign Call → FAIL ❌
  └─ Fallback #3 (return)

After 3 failures → CIRCUIT OPENS 🔴

Request #4-10
  ├─ (Skip Feign Call) - Circuit is OPEN
  └─ Return Fallback IMMEDIATELY (no network delay)

After timeout → Circuit HALF-OPEN (tries once)

Request #11
  ├─ Try Feign Call → SUCCESS ✅
  └─ Circuit CLOSES 🟢 (back to normal)
```

---

## Practical E-Commerce Example

### Scenario: Order Service calls Payment Service

**Feign Client:**
```java
@FeignClient(
    name = "payment-service",
    fallback = PaymentClientFallback.class
)
public interface PaymentClient {
    @PostMapping("/charge")
    PaymentResponse processPayment(@RequestBody PaymentRequest request);
}

@Component
public class PaymentClientFallback implements PaymentClient {
    @Override
    public PaymentResponse processPayment(PaymentRequest request) {
        // Payment service is down
        // Return PENDING status
        return PaymentResponse.builder()
            .status("PENDING")
            .message("Payment pending - will retry")
            .transactionId("RETRY-" + UUID.randomUUID())
            .build();
    }
}

// Usage
@Service
public class OrderService {
    @Autowired
    private PaymentClient paymentClient;
    
    public Order placeOrder(OrderRequest request) {
        Order order = createOrder(request);
        
        // Call Payment Service
        PaymentResponse payment = paymentClient.processPayment(
            new PaymentRequest(order.getId(), order.getTotal())
        );
        
        if ("PENDING".equals(payment.getStatus())) {
            order.setStatus("PAYMENT_PENDING");  // Retry later
        } else {
            order.setStatus("CONFIRMED");
        }
        
        return order;
    }
}
```

---

## When to Use Which

### Use Feign Fallback:
- ✅ Occasional network glitches
- ✅ Brief service downtime (< 1 minute)
- ✅ Graceful degradation acceptable
- ✅ No need to stop requests immediately

### Use Circuit Breaker:
- ✅ Service is down for extended period
- ✅ Want to prevent cascading failures
- ✅ Need fast response (no network wait)
- ✅ Want automatic recovery detection

### Use Both Together:
```java
@FeignClient(
    name = "payment-service",
    fallback = PaymentClientFallback.class
)
public interface PaymentClient {
    @PostMapping("/charge")
    @CircuitBreaker(name = "payment", fallbackMethod = "chargeFallback")
    PaymentResponse processPayment(@RequestBody PaymentRequest request);
    
    default PaymentResponse chargeFallback(PaymentRequest request, Exception e) {
        // Circuit Breaker fallback
        return new PaymentResponse("CIRCUIT_OPEN");
    }
}
```

**Flow with Both:**
```
First few failures → Feign Fallback (retry logic)
  ↓
Still failing → Circuit Breaker opens
  ↓
Circuit Breaker Fallback (fast response)
  ↓
Service recovers → Circuit closes
  ↓
Back to normal Feign behavior
```

---

## Configuration Example

```yaml
# application.yml
spring:
  cloud:
    openfeign:
      client:
        config:
          payment-service:
            connect-timeout-millis: 5000
            read-timeout-millis: 10000
            logger-level: full
            
resilience4j:
  circuitbreaker:
    configs:
      default:
        register-health-indicator: true
        sliding-window-size: 10
        failure-rate-threshold: 50
        wait-duration-in-open-state: 30000
        permitted-number-of-calls-in-half-open-state: 3
```

---

## Summary

| Aspect | Feign Client |
|--------|---|
| **Purpose** | Declarative REST client for microservices |
| **When to Use** | Service-to-service communication |
| **Fallback** | Returns alternative response on failure |
| **Best Paired With** | Circuit Breaker for resilience |
| **Response Time** | Slower (waits for timeout on failure) |
| **Use Case** | Simple, occasional failures |

---

*Last Updated: 2026-10-03*
*Feign Client Best Practices for Microservices! 🐦*
