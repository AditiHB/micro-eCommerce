# Rate Limiting in Microservices 🚦

Control and manage traffic flow in microservices to prevent abuse and ensure fair resource utilization.

---

## What is Rate Limiting?

Rate limiting is a technique to control the amount of traffic (requests) a user or client can make to your service.

### Simple Definition:

Instead of allowing unlimited requests:
```
❌ WITHOUT Rate Limiting:
User sends 10,000 requests/second
  ↓
Server overwhelmed
  ↓
All users suffer
  ↓
Service crashes
```

Use rate limiting:
```
✅ WITH Rate Limiting:
User sends 10,000 requests/second
  ↓
Rate limiter: "Whoa! Max 100 req/sec per user"
  ↓
Rate limiter blocks extra requests
  ↓
Server stays healthy
  ↓
Fair for everyone
```

---

## Why Rate Limiting Matters

```
PROBLEMS RATE LIMITING SOLVES:
═════════════════════════════════════════

1. ABUSE PREVENTION
   ├─ Malicious users can't overwhelm service
   ├─ DDoS attacks mitigated
   └─ Bots/scrapers blocked

2. FAIR RESOURCE SHARING
   ├─ Each user gets fair share
   ├─ Prevents one user from hogging resources
   └─ Better experience for everyone

3. COST CONTROL
   ├─ Limit API usage to control costs
   ├─ Prevent runaway bills
   └─ Predictable infrastructure costs

4. COMPLIANCE & SLA
   ├─ Meet Service Level Agreements
   ├─ Ensure quality of service
   └─ Regulatory requirements (GDPR, etc.)

5. SERVICE PROTECTION
   ├─ Prevent cascading failures
   ├─ Protect database from overload
   └─ Maintain API stability

6. FEATURE MONETIZATION
   ├─ Tier users (free, pro, enterprise)
   ├─ Different rate limits per tier
   └─ Premium users get higher limits
```

---

## Rate Limiting Strategies

### 1. Fixed Window (Token Bucket) 🪣

Most common and simplest approach.

**How it works:**
```
FIXED WINDOW - Fixed Time Period:
═════════════════════════════════════════

User: 100 requests per minute

Minute 1 (00:00 - 00:59)
├─ Requests 1-50: ✅ ALLOWED
├─ Requests 51-100: ✅ ALLOWED
└─ Request 101: ❌ BLOCKED (limit reached)

Minute 2 (01:00 - 01:59)
├─ Counter RESETS
├─ Requests 1-100: ✅ ALLOWED
└─ Request 101: ❌ BLOCKED
```

**Timeline:**
```
Time: 00:58 (2 seconds before reset)
├─ User has 5 requests left
└─ Makes 100 requests in 2 seconds → All blocked

Time: 01:00 (Reset!)
├─ Counter resets to 100
└─ Same 100 requests are now allowed!

Problem: Burst at window boundaries!
```

---

### 2. Sliding Window 🪟

Smoother approach that prevents burst attacks.

**How it works:**
```
SLIDING WINDOW - Continuous Check:
═════════════════════════════════════════

User: 100 requests per minute

Time 10:00:00 - Make 100 requests → ✅ All allowed
Time 10:00:15 - Make 1 request → Check last 60 sec
                 Last 60 sec (10:00:00-10:00:15) = 100 req
                 → ❌ BLOCKED

Time 10:00:30 - Make 1 request → Check last 60 sec
                 Last 60 sec (10:00:30-10:01:30) = 50 req
                 → ✅ ALLOWED

Time 10:01:00 - Make 100 requests → Check last 60 sec
                 Last 60 sec (10:01:00-10:02:00) = 50 + 100 = 150
                 → ❌ BLOCKED (150 > 100 limit)
```

**Advantages:**
- No burst at window boundaries
- More fair distribution
- Better for burst traffic

---

### 3. Token Bucket 🎫

Each request consumes a token.

**How it works:**
```
TOKEN BUCKET:
═════════════════════════════════════════

Bucket capacity: 100 tokens
Refill rate: 10 tokens/second

Initial state:
├─ Bucket = 100 tokens (full)

Request 1: Takes 1 token → 99 tokens left ✅
Request 2: Takes 1 token → 98 tokens left ✅
...
Request 100: Takes 1 token → 0 tokens left ✅
Request 101: Takes 1 token → Need 1, have 0 → ❌ BLOCKED

Meanwhile, bucket refills:
├─ After 1 sec: +10 tokens = 10 tokens
├─ After 2 sec: +10 tokens = 20 tokens
└─ After 10 sec: +10 tokens = 100 tokens (full again)

Request 101 (after 2 sec): Takes 1 token → 19 tokens ✅
```

**Advantages:**
- Allows bursts (up to bucket size)
- Smooth refill rate
- Fair for variable traffic

---

### 4. Leaky Bucket 💧

Requests queue up, processed at fixed rate.

**How it works:**
```
LEAKY BUCKET - Queue Processing:
═════════════════════════════════════════

Queue size: 100 requests
Leak rate: 10 requests/second

Requests arrive:
├─ Requests 1-100: Added to queue ✅
├─ Request 101: Queue full → ❌ BLOCKED
└─ Request 102: Queue full → ❌ BLOCKED

Processing (leak):
├─ 1 sec: 10 requests processed (queue = 90)
├─ 2 sec: 10 requests processed (queue = 80)
└─ 10 sec: 10 requests processed (queue = 0)

New requests:
├─ Request 101: Queue has space → ✅ Added to queue
└─ Processed after 10+ seconds
```

**Advantages:**
- Very predictable (fixed processing rate)
- Smooths out traffic spikes
- Good for backend protection

---

## Comparison of Algorithms

| Algorithm | Burst | Smoothness | Complexity | Best For |
|-----------|-------|-----------|-----------|----------|
| **Fixed Window** | ❌ Spikey | ⭐ Poor | ⭐ Simple | Simple APIs |
| **Sliding Window** | ✅ Smooth | ⭐⭐⭐ Good | ⭐⭐ Medium | Most use cases |
| **Token Bucket** | ✅ Allow burst | ⭐⭐⭐ Good | ⭐⭐ Medium | Variable traffic |
| **Leaky Bucket** | ❌ No burst | ⭐⭐⭐⭐⭐ Smooth | ⭐⭐⭐ Complex | Backend protection |

---

## Rate Limiting Levels

### 1. API Level Rate Limiting

```java
// Per-user rate limiting
@RestController
public class UserController {
    
    @RateLimiter(name = "userAPI", limitPerSecond = 10)
    @GetMapping("/users/{id}")
    public User getUser(@PathVariable String id) {
        return userService.getUser(id);
    }
}
```

---

### 2. Endpoint Level Rate Limiting

```java
// Different limits for different endpoints
@RestController
public class OrderController {
    
    // Read operations: Higher limit
    @RateLimiter(limitPerSecond = 100)
    @GetMapping("/orders")
    public List<Order> getOrders() {
        return orderService.getOrders();
    }
    
    // Write operations: Lower limit
    @RateLimiter(limitPerSecond = 10)
    @PostMapping("/orders")
    public Order createOrder(@RequestBody OrderRequest request) {
        return orderService.createOrder(request);
    }
}
```

---

### 3. User/Client Level Rate Limiting

```java
// Different limits per user tier
public class User {
    private String id;
    private UserTier tier;  // FREE, PREMIUM, ENTERPRISE
}

// Free user: 100 req/hour
// Premium user: 10,000 req/hour
// Enterprise: Unlimited
```

---

### 4. Global Rate Limiting

```
GLOBAL LIMITS:
═════════════════════════════════════════

Total service capacity: 100,000 requests/minute

When total hits 100,000:
├─ All new requests → ❌ BLOCKED
└─ Until rate drops below threshold

Prevents service overload!
```

---

## Implementation Approaches

### 1. Spring Cloud CircuitBreaker + Resilience4j

```java
@Service
public class UserService {
    
    @RateLimiter(name = "userService", fallbackMethod = "getUserFallback")
    public User getUser(String id) {
        return userRepository.findById(id);
    }
    
    public User getUserFallback(String id, Exception e) {
        User user = new User();
        user.setId(id);
        user.setStatus("RATE_LIMITED");
        return user;
    }
}

// application.yml
resilience4j:
  ratelimiter:
    configs:
      default:
        register-health-indicator: true
        limit-refresh-period: 1m
        limit-for-period: 100
        timeout-duration: 5s
```

---

### 2. Spring Cloud Gateway Rate Limiting

```yaml
# application.yml
spring:
  cloud:
    gateway:
      routes:
        - id: user-service
          uri: http://user-service:8080
          predicates:
            - Path=/users/**
          filters:
            - name: RequestRateLimiter
              args:
                redis-rate-limiter:
                  replenish-rate: 100      # 100 req/second
                  burst-capacity: 200       # Allow burst up to 200
                  key-resolver: "#{@userKeyResolver}"
```

---

### 3. Bucket4j (Token Bucket Implementation)

```java
@Component
public class RateLimitingService {
    
    private final Map<String, Bucket> cache = new ConcurrentHashMap<>();
    
    public boolean allowRequest(String userId) {
        Bucket bucket = cache.computeIfAbsent(userId, k -> 
            Bucket4j.builder()
                .addSimpleState(
                    () -> TokensInheritance.constructState(1000, Instant.now()),
                    (numTokens, state) -> state.get(),
                    100,  // capacity
                    Duration.ofMinutes(1)
                )
                .build()
        );
        
        return bucket.tryConsume(1);  // Try to consume 1 token
    }
}

// Usage
@PostMapping("/orders")
public ResponseEntity<?> createOrder(@RequestBody OrderRequest request) {
    String userId = getCurrentUserId();
    
    if (!rateLimitingService.allowRequest(userId)) {
        return ResponseEntity
            .status(429)  // Too Many Requests
            .body("Rate limit exceeded");
    }
    
    return ResponseEntity.ok(orderService.createOrder(request));
}
```

---

### 4. Custom Annotation

```java
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RateLimited {
    int requestsPerSecond() default 100;
    String key() default "user";  // user, ip, custom
}

@Aspect
@Component
public class RateLimitingAspect {
    
    private final RateLimitingService rateLimitingService;
    
    @Around("@annotation(rateLimited)")
    public Object rateLimit(ProceedingJoinPoint joinPoint, 
                           RateLimited rateLimited) throws Throwable {
        String key = getKey(rateLimited.key());
        
        if (!rateLimitingService.allowRequest(key, rateLimited.requestsPerSecond())) {
            throw new RateLimitExceededException("Too many requests");
        }
        
        return joinPoint.proceed();
    }
}

// Usage
@RateLimited(requestsPerSecond = 100, key = "user")
@GetMapping("/users/{id}")
public User getUser(@PathVariable String id) {
    return userService.getUser(id);
}
```

---

## Rate Limiting Headers

When a client gets rate limited, return HTTP headers:

```
HTTP/1.1 429 Too Many Requests

X-RateLimit-Limit: 100           # Max requests per period
X-RateLimit-Remaining: 0         # Requests left this period
X-RateLimit-Reset: 1633024260    # Unix timestamp when limit resets
Retry-After: 60                  # Seconds to wait before retry
```

**Client can read these to:**
- Stop making requests immediately
- Wait and retry after `Retry-After`
- Implement exponential backoff

---

## Distributed Rate Limiting

### Problem: Multiple Instances

```
WITHOUT distributed rate limiting:

Instance 1 (100 req/min)
└─ User makes 100 requests → ✅ All allowed

Instance 2 (100 req/min)
└─ Same user makes 100 requests → ✅ All allowed

Total: 200 requests allowed (should be 100!)

Solution: Shared store (Redis)
```

### Solution: Redis-Backed Rate Limiting

```java
@Component
public class RedisRateLimitingService {
    
    @Autowired
    private RedisTemplate<String, Long> redisTemplate;
    
    public boolean allowRequest(String userId, int limitPerMinute) {
        String key = "rate-limit:" + userId;
        Long currentCount = redisTemplate.opsForValue().increment(key);
        
        if (currentCount == 1) {
            // First request in window, set expiry
            redisTemplate.expire(key, Duration.ofMinutes(1));
        }
        
        return currentCount <= limitPerMinute;
    }
}

// Spring Cloud Gateway + Redis
spring:
  cloud:
    gateway:
      routes:
        - id: user-service
          uri: http://user-service:8080
          filters:
            - name: RequestRateLimiter
              args:
                redis-rate-limiter:
                  replenish-rate: 100
                  burst-capacity: 200
```

---

## E-Commerce Rate Limiting Example

### Scenario: Payment Processing

```java
@RestController
@RequestMapping("/api/payments")
public class PaymentController {
    
    // Strict limit for payment operations
    @RateLimiter(
        limitPerSecond = 5,
        windowSize = 60,  // per minute
        fallbackMethod = "paymentLimitExceeded"
    )
    @PostMapping("/charge")
    public ResponseEntity<PaymentResponse> chargeCard(
            @RequestBody ChargeRequest request) {
        
        return ResponseEntity.ok(paymentService.charge(request));
    }
    
    public ResponseEntity<PaymentResponse> paymentLimitExceeded(
            ChargeRequest request, Exception e) {
        
        return ResponseEntity
            .status(429)
            .body(new PaymentResponse("RATE_LIMITED", "Too many payment requests"));
    }
    
    // Higher limit for read operations
    @RateLimiter(limitPerSecond = 100)
    @GetMapping("/transactions/{id}")
    public ResponseEntity<Transaction> getTransaction(
            @PathVariable String id) {
        
        return ResponseEntity.ok(paymentService.getTransaction(id));
    }
    
    // Admin operations with high limits
    @RateLimiter(limitPerSecond = 1000, requiresRole = "ADMIN")
    @GetMapping("/admin/analytics")
    public ResponseEntity<Analytics> getAnalytics() {
        return ResponseEntity.ok(analyticsService.getAnalytics());
    }
}
```

---

## Best Practices

### ✅ DO's

```java
// 1. Different limits for different operations
@RateLimiter(limitPerSecond = 10)     // Write
public void createOrder() { }

@RateLimiter(limitPerSecond = 100)    // Read
public Order getOrder() { }

// 2. Per-user rate limiting
String userId = SecurityContextHolder.getContext()
    .getAuthentication().getName();

// 3. Return proper HTTP status
if (rateLimitExceeded) {
    return ResponseEntity.status(429).build();  // 429 Too Many Requests
}

// 4. Include rate limit headers
response.setHeader("X-RateLimit-Limit", "100");
response.setHeader("X-RateLimit-Remaining", "42");
response.setHeader("Retry-After", "60");

// 5. Log rate limit violations
logger.warn("User {} exceeded rate limit", userId);

// 6. Use Redis for distributed limits
// Spring Cloud Gateway with Redis backend

// 7. Implement graceful degradation
public Order getOrderFallback(String id, Exception e) {
    return cachedOrderService.getCachedOrder(id);
}
```

### ❌ DON'Ts

```java
// 1. Don't use same limit for all operations
@RateLimiter(limitPerSecond = 100)
public void createOrder() { }  // ❌ Too high

// 2. Don't forget distributed rate limiting
// Single instance rate limiting in microservices  ❌

// 3. Don't return wrong HTTP status
return ResponseEntity.status(401).build();  // ❌ Should be 429

// 4. Don't forget to set expiry on keys
cache.put(userId, count);  // ❌ Memory leak!

// 5. Don't ignore rate limit bursts
// User burst causes entire service outage  ❌

// 6. Don't rate limit critical endpoints
@RateLimiter(limitPerSecond = 1)
public HealthCheck healthCheck() { }  // ❌ Don't limit

// 7. Don't expose implementation details
response.setHeader("X-Custom-RateLimit", "secret-info");  // ❌
```

---

## Handling Rate Limit Exceeded

### Client-Side Exponential Backoff

```java
public class ResilientApiClient {
    
    private static final int MAX_RETRIES = 3;
    
    public <T> T executeWithRetry(Supplier<T> request, 
                                   Class<T> responseType) {
        int attempt = 0;
        long delayMs = 1000;  // Start with 1 second
        
        while (attempt < MAX_RETRIES) {
            try {
                return request.get();  // Try request
            } catch (RateLimitException e) {
                attempt++;
                if (attempt >= MAX_RETRIES) {
                    throw e;
                }
                
                // Exponential backoff: 1s, 2s, 4s
                Thread.sleep(delayMs);
                delayMs *= 2;
            }
        }
        
        throw new RuntimeException("Max retries exceeded");
    }
}

// Usage
apiClient.executeWithRetry(
    () -> paymentClient.chargeCard(request),
    PaymentResponse.class
);
```

---

## Monitoring Rate Limiting

```java
@Component
public class RateLimitMetrics {
    
    private MeterRegistry meterRegistry;
    
    public void recordRateLimitExceeded(String userId) {
        Counter.builder("rate_limit.exceeded")
            .tag("user", userId)
            .register(meterRegistry)
            .increment();
    }
    
    public void recordRateLimitAllowed(String userId) {
        Counter.builder("rate_limit.allowed")
            .tag("user", userId)
            .register(meterRegistry)
            .increment();
    }
}

// Metrics in Prometheus:
# Total rate limit violations
rate_limit_exceeded_total{user="user123"} 150

# Rate limit hit rate
rate_limit_exceeded_total / (rate_limit_exceeded_total + rate_limit_allowed_total)
```

---

## Summary

| Aspect | Details |
|--------|---------|
| **Purpose** | Control traffic, prevent abuse, protect service |
| **Algorithms** | Fixed Window, Sliding Window, Token Bucket, Leaky Bucket |
| **Best For** | Token Bucket for APIs, Leaky Bucket for backend |
| **Implementation** | Resilience4j, Spring Cloud Gateway, Bucket4j, Redis |
| **Distributed** | Always use Redis in microservices |
| **HTTP Status** | 429 Too Many Requests |
| **Headers** | X-RateLimit-Limit, X-RateLimit-Remaining, Retry-After |
| **Client Handling** | Exponential backoff retry strategy |

---

*Last Updated: 2026-10-03*
*Rate Limiting Best Practices for Microservices! 🚦*
