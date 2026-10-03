# Transactional Annotation in This Project 🏪

## The Micro-eCommerce System

This e-commerce project uses transactions to ensure data consistency across all services.

---

## Service Architecture

```
┌──────────────────────────────────────────────────┐
│                                                  │
│   📦 Order Service                               │
│   💳 Payment Service         → Uses @Transactional
│   📊 Inventory Service                           │
│   📧 Notification Service                        │
│   🚚 Delivery Service                            │
│                                                  │
└──────────────────────────────────────────────────┘
              ↓
          DATABASE (MySQL)
          with Transaction Support
```

---

## How Transactions Work in Each Service

### 1. Order Service 📦

```java
@Service
public class OrderService {
    
    @Transactional
    public Order placeOrder(OrderRequest request) {
        // All operations in one transaction
        
        // Step 1: Create order
        Order order = new Order();
        order.setCustomerId(request.getCustomerId());
        order.setItems(request.getItems());
        order.setStatus("PENDING");
        orderRepository.save(order);
        
        // Step 2: Check inventory
        for (OrderItem item : request.getItems()) {
            inventoryService.deductStock(item.getProductId(), item.getQuantity());
        }
        
        // Step 3: Process payment
        paymentService.processPayment(order.getTotal(), request.getCardToken());
        
        // Step 4: Update order status
        order.setStatus("CONFIRMED");
        orderRepository.save(order);
        
        return order;
    }
    // If ANY step fails → ROLLBACK everything
}
```

**What happens:**
- All 4 steps happen together
- If payment fails (step 3): Steps 1-2 get rolled back
- Inventory is restored
- Order status reverts to original
- Database stays consistent! ✅

---

### 2. Payment Service 💳

```java
@Service
public class PaymentService {
    
    @Transactional
    public Payment processPayment(String orderId, double amount, String cardToken) {
        Payment payment = new Payment();
        payment.setOrderId(orderId);
        payment.setAmount(amount);
        payment.setStatus("PENDING");
        paymentRepository.save(payment);
        
        try {
            // Call external API (bank)
            ChargeResponse response = bankAPI.charge(cardToken, amount);
            
            if (!response.isSuccess()) {
                throw new PaymentException("Card declined: " + response.getCode());
            }
            
            // Update payment status
            payment.setStatus("COMPLETED");
            payment.setTransactionId(response.getTransactionId());
            paymentRepository.save(payment);
            
            return payment;
            
        } catch (PaymentException e) {
            // Exception triggers ROLLBACK
            payment.setStatus("FAILED");
            payment.setErrorMessage(e.getMessage());
            // ROLLBACK here
            throw e;  // Re-throw to trigger rollback!
        }
    }
}
```

**What happens:**
- Unchecked exception `PaymentException` → ROLLBACK
- Payment stays in "PENDING" status
- No incorrect "COMPLETED" state in DB
- Clean failure state ✅

---

### 3. Inventory Service 📊

```java
@Service
public class InventoryService {
    
    @Transactional
    public void deductStock(String productId, int quantity) {
        // Get product (with pessimistic lock)
        Product product = productRepository.findByIdWithLock(productId);
        
        if (product.getStock() < quantity) {
            throw new InsufficientStockException("Not enough stock");
        }
        
        // Deduct stock
        product.setStock(product.getStock() - quantity);
        productRepository.save(product);
        
        // Log inventory change
        InventoryLog log = new InventoryLog();
        log.setProductId(productId);
        log.setQuantityChanged(-quantity);
        log.setReason("ORDER");
        inventoryLogRepository.save(log);
    }
}
```

**What happens:**
- Product stock deducted
- Inventory log created
- If either fails: ROLLBACK both
- Locks prevent race conditions
- Stock always accurate ✅

---

## Real Order Flow Example 🛒

```
Customer places order for:
├─ Product A (qty: 2)
├─ Product B (qty: 1)
└─ Total: $50

ORDER PROCESSING WITH TRANSACTIONS:
═════════════════════════════════════

@Transactional (SINGLE TRANSACTION)
OrderService.placeOrder() {
    
    ┌─────────────────────────────────┐
    │ Step 1: Create Order           │
    │ ├─ Order ID: 12345             │
    │ ├─ Status: PENDING             │
    │ └─ Amount: $50                 │
    │ ✅ SAVED (pending)             │
    └────────┬────────────────────────┘
             ↓
    ┌─────────────────────────────────┐
    │ Step 2: Inventory Service      │
    │ (Another @Transactional method)│
    │ ├─ Product A: 100 → 98         │
    │ ├─ Product B: 50 → 49          │
    │ └─ Log created                 │
    │ ✅ SAVED (pending)             │
    └────────┬────────────────────────┘
             ↓
    ┌─────────────────────────────────┐
    │ Step 3: Payment Service        │
    │ (Another @Transactional method)│
    │ ├─ Call Bank API               │
    │ ├─ Response: APPROVED ✅       │
    │ ├─ Transaction ID: TXN123      │
    │ └─ Payment status: COMPLETED   │
    │ ✅ SAVED (pending)             │
    └────────┬────────────────────────┘
             ↓
    ┌─────────────────────────────────┐
    │ Step 4: Update Order Status   │
    │ ├─ Order 12345                 │
    │ └─ Status: CONFIRMED           │
    │ ✅ SAVED (pending)             │
    └────────┬────────────────────────┘
             ↓
    ┌─────────────────────────────────┐
    │ ALL STEPS SUCCESSFUL ✅        │
    │ COMMIT TRANSACTION             │
    └────────┬────────────────────────┘
             ↓
    DATABASE FINAL STATE:
    ├─ Order 12345: CONFIRMED ✅
    ├─ Product A stock: 98 ✅
    ├─ Product B stock: 49 ✅
    ├─ Payment: COMPLETED ✅
    └─ Inventory log: created ✅


SCENARIO: PAYMENT FAILS
═════════════════════════════════════

...Steps 1-2 successful...
             ↓
    ┌─────────────────────────────────┐
    │ Step 3: Payment Service        │
    │ ├─ Call Bank API               │
    │ ├─ Response: DECLINED ❌       │
    │ └─ PaymentException thrown!    │
    └────────┬────────────────────────┘
             ↓
    ┌─────────────────────────────────┐
    │ EXCEPTION DETECTED! 💥         │
    │ ENTIRE TRANSACTION ROLLBACK!   │
    └────────┬────────────────────────┘
             ↓
    DATABASE REVERTED STATE:
    ├─ Order: Never created ✅
    ├─ Product A stock: 100 (restored) ✅
    ├─ Product B stock: 50 (restored) ✅
    ├─ Payment: Never processed ✅
    └─ No inventory log created ✅
    
    Result: Clean state! No partial order! ✅
```

---

## Propagation Examples in Project

### Scenario 1: PROPAGATION.REQUIRED (Default)

```java
// OrderService
@Transactional
public void placeOrder(OrderRequest request) {
    // Transaction created
    inventoryService.deductStock(...);
    // Calls deductStock with propagation=REQUIRED
    // SAME TRANSACTION! ✅
}

// InventoryService
@Transactional(propagation = Propagation.REQUIRED)
public void deductStock(String productId, int quantity) {
    // Uses same transaction from placeOrder
    // If fails here → entire placeOrder rollbacks
}
```

**Result:** One big transaction. If inventory fails, order fails!

---

### Scenario 2: PROPAGATION.REQUIRES_NEW

```java
// OrderService
@Transactional
public void placeOrder(OrderRequest request) {
    // Main transaction
    inventoryService.deductStock(...);
    // Calls deductStock with propagation=REQUIRES_NEW
    // NEW TRANSACTION! ✅
}

// InventoryService
@Transactional(propagation = Propagation.REQUIRES_NEW)
public void deductStock(String productId, int quantity) {
    // Independent transaction!
    // Succeeds even if order fails
}
```

**Result:** Inventory update succeeds independently. Order can still rollback!

---

### Scenario 3: Audit Logging with REQUIRES_NEW

```java
@Service
public class OrderService {
    
    @Transactional
    public void placeOrder(OrderRequest request) {
        // Main transaction
        Order order = createOrder(request);
        processPayment(order);
        
        // Log audit (independent transaction)
        auditService.logOrderCreation(order);
    }
}

@Service
public class AuditService {
    
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logOrderCreation(Order order) {
        // Independent transaction!
        // Even if main order fails
        // Audit log is still created ✅
        AuditLog log = new AuditLog();
        log.setOrderId(order.getId());
        log.setAction("CREATE");
        log.setTimestamp(LocalDateTime.now());
        auditLogRepository.save(log);
    }
}
```

**Result:** Audit trail always recorded, even on failures!

---

## Exception Handling Examples

### Good: Order Service with Proper Exception Handling

```java
@Service
public class OrderService {
    
    @Transactional
    public Order placeOrder(OrderRequest request) throws OrderException {
        try {
            Order order = createOrder(request);
            
            inventoryService.deductStock(order.getItems());
            
            paymentService.processPayment(order.getTotal(), request.getCardToken());
            
            order.setStatus("CONFIRMED");
            orderRepository.save(order);
            
            return order;
            
        } catch (InsufficientStockException e) {
            // Re-throw to trigger rollback
            throw new OrderException("Insufficient stock: " + e.getMessage(), e);
        } catch (PaymentException e) {
            // Re-throw to trigger rollback
            throw new OrderException("Payment failed: " + e.getMessage(), e);
        }
        // No silent catches! ✅
    }
}
```

**Result:** Exception propagates → ROLLBACK ✅

---

### Bad: Order Service with Eaten Exception

```java
@Service
public class OrderService {
    
    @Transactional
    public Order placeOrder(OrderRequest request) {
        Order order = createOrder(request);
        inventoryService.deductStock(order.getItems());
        
        try {
            paymentService.processPayment(order.getTotal(), request.getCardToken());
        } catch (PaymentException e) {
            logger.error("Payment failed", e);
            // ❌ EATEN EXCEPTION! No re-throw!
        }
        
        order.setStatus("CONFIRMED");
        orderRepository.save(order);
        
        return order;  // Returns normally!
    }
}
// Spring does: COMMIT! 😱
// Result: Order with no payment! 💥
```

**Result:** Exception swallowed → COMMIT (wrong!) ❌

---

## Attribute Combinations Used in Project

### Standard Order Processing

```java
@Transactional
public Order placeOrder(OrderRequest request) {
    // Propagation: REQUIRED (default)
    // Isolation: DEFAULT (READ_COMMITTED)
    // Timeout: -1 (no timeout)
    // ReadOnly: false
    // RollbackFor: unchecked exceptions
}
```

---

### Read-Only Operations

```java
@Transactional(readOnly = true)
public Order getOrderById(String orderId) {
    // No writes allowed
    // Isolation: DEFAULT
    // Can optimize query (hint database)
    return orderRepository.findById(orderId);
}

@Transactional(readOnly = true)
public List<Order> getOrdersByCustomer(String customerId) {
    // Multiple reads in single transaction
    // Consistent snapshot
    return orderRepository.findByCustomerId(customerId);
}
```

---

### Long-Running Transactions (Risky!)

```java
@Transactional(timeout = 30)  // 30 seconds max
public void processOrderBatch(List<Order> orders) {
    for (Order order : orders) {
        processOrder(order);
    }
    // Transaction fails if takes > 30 seconds
}
```

---

### Custom Exception Handling

```java
@Transactional(
    rollbackFor = {OutOfStockException.class, PaymentFailedException.class},
    noRollbackFor = {WarningException.class}
)
public Order placeOrder(OrderRequest request) throws OrderException {
    // Rollback on OUT_OF_STOCK
    // Rollback on PAYMENT_FAILED
    // DON'T rollback on WARNING
}
```

---

## Common Issues and Solutions

### Issue 1: Exception Getting Eaten

```java
❌ WRONG:
@Transactional
public void transfer() {
    try {
        payment.process();
    } catch (PaymentException e) {
        // Silent catch!
    }
}

✅ CORRECT:
@Transactional
public void transfer() {
    try {
        payment.process();
    } catch (PaymentException e) {
        logger.error("Payment failed", e);
        throw new RuntimeException("Transfer failed", e);
    }
}
```

---

### Issue 2: Private Method Doesn't Have Transaction

```java
❌ WRONG:
@Service
public class OrderService {
    
    @Transactional
    public void placeOrder() {
        completeOrder();  // ❌ Bypasses proxy!
    }
    
    @Transactional
    private void completeOrder() {
        // This @Transactional is ignored!
    }
}

✅ CORRECT:
@Service
public class OrderService {
    
    @Transactional
    public void placeOrder() {
        // Transactional here
    }
    
    @Transactional
    public void completeOrder() {
        // Both are public, both work!
    }
}
```

---

### Issue 3: Self-Invocation Doesn't Use Proxy

```java
❌ WRONG:
@Service
public class OrderService {
    
    @Transactional
    public void placeOrder() {
        createOrder();      // Uses proxy ✅
        this.applyDiscount();  // Bypasses proxy! ❌
    }
    
    @Transactional(readOnly = true)
    public void applyDiscount() {
        // This is called directly, not through proxy!
    }
}

✅ CORRECT:
@Service
public class OrderService {
    
    @Autowired
    private OrderService self;  // Inject self
    
    @Transactional
    public void placeOrder() {
        createOrder();
        self.applyDiscount();  // Uses proxy! ✅
    }
    
    @Transactional(readOnly = true)
    public void applyDiscount() {
        // Called through proxy now!
    }
}
```

---

## Production Checklist ✅

```
TRANSACTION SETUP ✅
├─ All write operations have @Transactional
├─ Read-only methods use readOnly=true
├─ Exception handling is correct (re-throw)
├─ No silent exception catches
└─ No private @Transactional methods

ATOMICITY ✅
├─ All related operations in same transaction
├─ No partial updates possible
├─ Rollback works for all failure scenarios
└─ Database stays consistent

ISOLATION ✅
├─ Isolation level appropriate for use case
├─ No dirty reads
├─ No lost updates
└─ Concurrent operations safe

DURABILITY ✅
├─ Database backed up regularly
├─ Transaction logs available
├─ Recovery procedures tested
└─ RTO/RPO defined

MONITORING ✅
├─ Long-running transactions detected
├─ Deadlock monitoring active
├─ Transaction rollbacks logged
└─ Performance metrics tracked
```

---

*Last Updated: 2026-10-03*
*Project-Specific Transaction Implementation 🏪*
