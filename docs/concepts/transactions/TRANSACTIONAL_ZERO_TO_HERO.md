# Transactional Annotation Zero to Hero 🚀

Complete master guide to mastering Spring @Transactional annotation and distributed transactions! From basics to production-ready expertise.

---

## Part 1: Foundation (Already Covered, Quick Review)

### The Basics You Know ✅

**Transaction = All or Nothing**
```
START TRANSACTION
  ├─ Operation 1
  ├─ Operation 2
  ├─ Operation 3
  └─ All succeed? COMMIT ✅
    Any fails? ROLLBACK ❌
END TRANSACTION
```

**ACID Properties:**
- **Atomicity:** All or nothing
- **Consistency:** Valid state always
- **Isolation:** No interference
- **Durability:** Once committed, permanent

**@Transactional Annotation:**
- Marks a method as transactional
- Spring manages commit/rollback automatically
- Unchecked exceptions cause rollback
- Checked exceptions don't (unless configured)

---

## Part 2: Distributed Transactions 🌐

### The Challenge: Multiple Databases

```
System Architecture:
┌──────────────────────────────────────────┐
│                                          │
│ ┌──────────┐    ┌──────────┐  ┌────────┐│
│ │  Order   │ →  │ Payment  │→ │Inventory││
│ │Database  │    │ Database │  │Database ││
│ └──────────┘    └──────────┘  └────────┘│
│                                          │
└──────────────────────────────────────────┘

ONE TRANSACTION, THREE DATABASES!

Challenge: How do we ensure atomicity across all three?
```

---

### Local Transactions (Single Database)

**EASY - Managed by database:**
```java
@Transactional
public void transfer(String from, String to, double amount) {
    // Single database, single transaction
    // Database ensures atomicity
    // Spring + Database handle everything
    
    debitAccount(from, amount);
    creditAccount(to, amount);
    // ✅ Easy: Both tables in same database
}
```

---

### Distributed Transactions (Multiple Databases)

**HARD - Requires coordination:**
```
Database 1:        Database 2:        Database 3:
(Order Service)    (Payment Service)   (Inventory Service)

Transaction X      Transaction Y       Transaction Z
├─ Create order    ├─ Process charge   ├─ Deduct stock
├─ Status: PENDING ├─ Status: PENDING  ├─ Log: PENDING
└─ Pending         └─ Pending          └─ Pending

All succeed?       All fail?           Mixed?
│                  │                   │
COMMIT ALL         ROLLBACK ALL        ROLLBACK ALL
      ✅                ❌                  ❌

No partial success allowed!
```

---

### Two-Phase Commit (2PC) Protocol 🤝

**The Solution for Distributed Transactions:**

```
PARTICIPANTS:
├─ Coordinator (Master) - Makes final decision
├─ Database 1 - Order Service
├─ Database 2 - Payment Service
└─ Database 3 - Inventory Service

PHASE 1: PREPARE (Voting)
════════════════════════════════════════════

Coordinator asks each database:
"Can you do your part?"

Order DB: "I can create order" → YES ✅
Payment DB: "I can process charge" → YES ✅
Inventory DB: "I can deduct stock" → YES ✅

All say YES → Proceed to Phase 2

═════════════════════════════════════════════

Inventory DB: "I can't deduct (out of stock)" → NO ❌

Not all YES → ABORT → Go to Rollback

═════════════════════════════════════════════

PHASE 2: COMMIT or ROLLBACK
════════════════════════════════════════════

If ALL said YES:
Coordinator: "COMMIT!"
├─ Order DB: COMMIT → Order saved
├─ Payment DB: COMMIT → Payment saved
└─ Inventory DB: COMMIT → Stock saved
Result: ALL changes permanent ✅

═════════════════════════════════════════════

If ANY said NO:
Coordinator: "ROLLBACK!"
├─ Order DB: ROLLBACK → Order reverted
├─ Payment DB: ROLLBACK → Payment reverted
└─ Inventory DB: ROLLBACK → Stock reverted
Result: ALL changes undone ✅

═════════════════════════════════════════════

GUARANTEE: All or nothing, across all databases!
```

---

## Part 3: @Transactional Attributes Deep Dive 🎯

### Propagation: How Transactions Propagate 📡

**PROPAGATION.REQUIRED (Default)**
```java
@Transactional(propagation = Propagation.REQUIRED)
public void method1() {
    // If no transaction: CREATE new one
    // If transaction exists: USE existing one
}

Use case: Nested calls all in same transaction
├─ method1() starts transaction
├─ method1() calls method2() → REQUIRED
│  └─ method2() uses method1()'s transaction
├─ Both succeed? COMMIT once
└─ Either fails? ROLLBACK both
```

**PROPAGATION.REQUIRES_NEW**
```java
@Transactional(propagation = Propagation.REQUIRES_NEW)
public void method1() {
    // ALWAYS create new transaction
    // Even if one exists, suspend it
}

Use case: Independent operations
├─ method1() starts transaction
├─ method1() calls method2() → REQUIRES_NEW
│  └─ method2() creates NEW transaction
│     Suspends method1()'s transaction
├─ method2() succeeds? COMMIT independently
├─ method2() fails? ROLLBACK independently
└─ method1() continues with its transaction
```

**PROPAGATION.SUPPORTS**
```java
@Transactional(propagation = Propagation.SUPPORTS)
public void method1() {
    // USE existing transaction if available
    // Otherwise run without transaction
}

Use case: Optional transaction
├─ If caller has transaction: USE it
└─ If caller has no transaction: RUN WITHOUT it
```

**PROPAGATION.NOT_SUPPORTED**
```java
@Transactional(propagation = Propagation.NOT_SUPPORTED)
public void method1() {
    // NEVER use transaction
    // Suspend existing transaction if any
}

Use case: Must run outside transaction
├─ Suspend parent transaction
├─ Run without transaction
├─ Resume parent when done
└─ Changes NOT part of parent transaction
```

**PROPAGATION.MANDATORY**
```java
@Transactional(propagation = Propagation.MANDATORY)
public void method1() {
    // REQUIRE existing transaction
    // If none exists: throw exception
}

Use case: MUST be called from transactional context
├─ Direct call (no transaction)? ERROR! ❌
└─ Called from @Transactional? OK! ✅
```

**PROPAGATION.NEVER**
```java
@Transactional(propagation = Propagation.NEVER)
public void method1() {
    // NEVER run in transaction
    // If transaction exists: throw exception
}

Use case: MUST NOT be in transaction
├─ Called from @Transactional? ERROR! ❌
└─ Direct call (no transaction)? OK! ✅
```

**PROPAGATION.NESTED**
```java
@Transactional(propagation = Propagation.NESTED)
public void method1() {
    // Create savepoint within existing transaction
    // If nested fails: rollback only nested part
    // If nested succeeds: commit with parent
}

Use case: Partial rollback
├─ method1() starts transaction
├─ method1() calls method2() → NESTED
│  └─ method2() creates SAVEPOINT
├─ method2() fails? ROLLBACK to savepoint
├─ method1() can continue or rollback everything
└─ Requires database support (MySQL, PostgreSQL)
```

---

### Isolation: Concurrency & Consistency 🔒

**Isolation Levels (From Weakest to Strongest):**

**1. READ_UNCOMMITTED** (Weakest)
```
Problems allowed:
├─ Dirty Read: Read uncommitted changes ❌
├─ Non-Repeatable Read: Value changes mid-transaction ❌
└─ Phantom Read: New rows appear mid-transaction ❌

When to use: NEVER in production!
Performance: Best
Consistency: Worst
```

**2. READ_COMMITTED** (Default)
```
Problems allowed:
├─ Dirty Read: NO ✅ (can't read uncommitted)
├─ Non-Repeatable Read: YES (value can change)
└─ Phantom Read: YES (new rows can appear)

When to use: Most applications (good balance)
Performance: Good
Consistency: Medium
```

**3. REPEATABLE_READ**
```
Problems allowed:
├─ Dirty Read: NO ✅
├─ Non-Repeatable Read: NO ✅ (snapshot of data)
└─ Phantom Read: YES (new rows can appear)

When to use: Need consistent reads
Performance: Slower
Consistency: Better
```

**4. SERIALIZABLE** (Strongest)
```
Problems allowed:
├─ Dirty Read: NO ✅
├─ Non-Repeatable Read: NO ✅
└─ Phantom Read: NO ✅ (no interference)

When to use: Critical operations (money!)
Performance: Slowest
Consistency: Best

Example: Bank transfers, financial systems
```

**Configuration Example:**
```java
@Transactional(isolation = Isolation.SERIALIZABLE)
public void transferMoney(String from, String to, double amount) {
    // Strictest isolation
    // Transactions behave as if serial (one after another)
    // Maximum consistency, minimum concurrency
}
```

---

### ReadOnly: Optimization Hint 📖

**Normal Transaction (Read + Write):**
```java
@Transactional
public Order getOrder(String orderId) {
    Order order = orderRepository.findById(orderId);
    order.setViewCount(order.getViewCount() + 1);
    orderRepository.save(order);  // WRITE
    return order;
}
```

**Read-Only Transaction:**
```java
@Transactional(readOnly = true)
public Order getOrder(String orderId) {
    // No writes allowed!
    Order order = orderRepository.findById(orderId);
    // This would throw exception:
    // orderRepository.save(order);  // ERROR!
    return order;
}
```

**Benefits:**
- Database can optimize (skip write ahead log)
- Prevents accidental writes
- Better for scaling read replicas
- Clearer intent

---

### Timeout: Prevent Long Transactions ⏰

**Without Timeout (Can hang forever):**
```java
@Transactional
public void processLargeDataset(List<Item> items) {
    for (Item item : items) {
        process(item);
        // If this takes 1 hour? Transaction waits 1 hour!
        // Locks held for 1 hour! ❌
    }
}
```

**With Timeout (30 seconds max):**
```java
@Transactional(timeout = 30)  // 30 seconds
public void processLargeDataset(List<Item> items) {
    for (Item item : items) {
        process(item);
    }
    // If takes > 30 seconds: TransactionTimedOutException ✅
}
```

**Setting:**
```java
@Transactional(timeout = 60)  // 60 seconds
@Transactional(timeout = -1)  // No timeout (default)
```

---

### Rollback & NoRollback: Exception Handling 🔄

**Default Behavior:**
- Unchecked exceptions → ROLLBACK ✅
- Checked exceptions → COMMIT ❌ (usually wrong!)

**Customizing with rollbackFor:**
```java
@Transactional(rollbackFor = {IOException.class, SQLException.class})
public void process() throws IOException {
    // These checked exceptions trigger rollback
    readFile();      // IOException → ROLLBACK
    queryDB();       // SQLException → ROLLBACK
    return something();
}
```

**Customizing with noRollbackFor:**
```java
@Transactional(noRollbackFor = {WarningException.class})
public void process() {
    validateData();   // WarningException → COMMIT (not rollback)
    saveData();
}
```

**Complex Configuration:**
```java
@Transactional(
    rollbackFor = {OutOfStockException.class, PaymentException.class},
    noRollbackFor = {WarningException.class}
)
public void placeOrder(OrderRequest request) {
    checkStock();      // OutOfStockException → ROLLBACK
    processPayment();  // PaymentException → ROLLBACK
    validateAddress(); // WarningException → COMMIT (not rollback)
}
```

---

## Part 4: Exception Handling in Transactions 🚨

### Unchecked Exceptions (Most Common)

```
Definition: Extends RuntimeException

Examples:
├─ NullPointerException
├─ IllegalArgumentException
├─ ArithmeticException
├─ CustomException extends RuntimeException
└─ PaymentException extends RuntimeException

Behavior in @Transactional:
└─ ALWAYS causes ROLLBACK ✅

Best for: Business errors that should rollback
```

**Example:**
```java
@Transactional
public void transfer(String from, String to, double amount) {
    Account fromAccount = getAccount(from);
    
    if (fromAccount == null) {
        throw new AccountNotFoundException("Account not found");
        // Unchecked exception → ROLLBACK ✅
    }
    
    debit(fromAccount, amount);
    credit(getAccount(to), amount);
}
```

---

### Checked Exceptions (Tricky!)

```
Definition: Extends Exception (not RuntimeException)

Examples:
├─ IOException
├─ SQLException
├─ FileNotFoundException
└─ ReflectiveOperationException

Behavior in @Transactional:
└─ DOES NOT cause rollback by default ❌

Problem: Usually you DO want rollback!
```

**Example (Wrong!):**
```java
@Transactional
public void process() throws IOException {
    readFile();      // IOException thrown
    saveData();      // Never executes
}
// Result: COMMIT! (Wrong!)
// Data from readFile partially saved ❌
```

**Solution: Use rollbackFor**
```java
@Transactional(rollbackFor = IOException.class)
public void process() throws IOException {
    readFile();      // IOException thrown
    saveData();      // Never executes
}
// Result: ROLLBACK! (Correct!) ✅
```

---

### Exception Eating (Silent Catches)

**Problem: Swallowing Exception**
```java
@Transactional
public void transfer() {
    debit();
    try {
        credit();    // Throws exception
    } catch (Exception e) {
        logger.error("Error", e);
        // ❌ Exception swallowed! No re-throw!
    }
    // Method ends normally
}
// Spring does: COMMIT! ❌ (Wrong!)
```

**Solution: Always Re-throw**
```java
@Transactional
public void transfer() throws TransferException {
    debit();
    try {
        credit();
    } catch (Exception e) {
        logger.error("Credit failed", e);
        throw new TransferException("Transfer failed", e);
        // ✅ Exception propagates → ROLLBACK
    }
}
```

**Or: Use throws without catch:**
```java
@Transactional
public void transfer() throws SQLException {
    debit();
    credit();  // SQLException propagates directly
    // ✅ Exception propagates → ROLLBACK
}
```

---

### How Exceptions Get Eaten 🍽️

**Scenario 1: Silent Catch Block**
```java
@Transactional
public void transfer() {
    try {
        charge();  // Throws PaymentException
    } catch (Exception e) {
        // Silent! No logging, no re-throw
    }
    // Exception never reaches Spring
}
// Result: COMMIT (wrong!)
```

**Scenario 2: Catch and Return**
```java
@Transactional
public boolean transfer() {
    try {
        charge();  // Throws PaymentException
    } catch (Exception e) {
        return false;  // Eaten! ❌
    }
    return true;
}
// Result: COMMIT (wrong!)
```

**Scenario 3: Catch and Log Only**
```java
@Transactional
public void transfer() {
    try {
        charge();  // Throws PaymentException
    } catch (Exception e) {
        logger.warn("Payment failed: " + e.getMessage());
        // ❌ Logged but eaten!
    }
}
// Result: COMMIT (wrong!)
```

**Scenario 4: Catch and Handle Partially**
```java
@Transactional
public void transfer() {
    try {
        charge();
        updateInventory();
    } catch (ChargeException e) {
        // Only handles ChargeException
        // But updateInventory succeeded!
        // Partial state: ❌
    }
}
```

---

## Part 5: Two-Phase Commit (2PC) Deep Dive 📋

### Why 2PC?

**Without 2PC (Risky):**
```
Bank DB: Debit $100 → Success ✅
Insurance DB: Credit $100 → Failure ❌

Result: Money disappeared! 💥
```

**With 2PC (Safe):**
```
Bank DB: Debit $100 → Prepared ✅
Insurance DB: Credit $100 → Prepared ✅

Both ready? YES → COMMIT both

Both ready? NO → ROLLBACK both
```

---

### 2PC Phases in Detail

**Phase 1: Prepare (Voting)**

```
Coordinator sends: "Can you do this?"

Each Participant:
├─ Acquires locks
├─ Executes transaction
├─ Reaches point before commit
├─ Votes: "YES I can commit" or "NO I can't"
└─ Waits for coordinator decision

Coordinator collects votes:
├─ All YES? → Go to Phase 2 (Commit)
└─ Any NO? → Go to Phase 2 (Rollback)
```

**Phase 2: Commit or Rollback**

```
If all voted YES:
Coordinator: "COMMIT!"
Each Participant:
├─ Commits changes
├─ Releases locks
└─ Confirms "COMMITTED"

═════════════════════════════════════════════

If any voted NO:
Coordinator: "ROLLBACK!"
Each Participant:
├─ Rolls back changes
├─ Releases locks
└─ Confirms "ROLLED BACK"
```

---

### 2PC Problems

**Problem 1: Blocking**
```
Participant 1: Locks resources
Participant 2: Waits for participant 1
Participant 3: Waits for participant 2

If participant 1 crashes:
└─ Everyone locked forever! 🔒
```

**Problem 2: Network Issues**
```
Coordinator: "COMMIT!"
Participant 1: COMMITS ✅
Participant 2: Network down ❌
Participant 3: Network down ❌

Inconsistent state!
├─ Participant 1 committed
└─ Participants 2,3 still locked
```

**Problem 3: Performance**
```
Locking for long time → Reduced concurrency
Slow network → Delays everything
Many participants → More coordination overhead
```

---

### When to Use 2PC

**Good Use Cases:**
- Few participants (2-3 databases)
- Short transactions (seconds)
- Financial systems (need strict consistency)
- Legacy systems requiring immediate consistency

**Bad Use Cases:**
- Many microservices (10+)
- Long-running operations
- High throughput systems
- Unpredictable network

---

## Part 6: Alternative to 2PC - Saga Pattern 📖

### Why Saga Pattern?

2PC is blocking and risky. Saga pattern:
- Breaks transaction into smaller steps
- Each step is independent transaction
- Compensating transactions for rollback
- More resilient to failures

---

### Choreography Saga

```
Service A                Service B              Service C
   │                        │                       │
   ├─ Start                 │                       │
   │  Order created ✅      │                       │
   │                        │                       │
   ├─ Emit "order.created"  │                       │
   │                        │                       │
   ├─────────────────────>  │                       │
   │                        ├─ Process payment ✅  │
   │                        │                       │
   │                        ├─ Emit "payment.done" │
   │                        │                       │
   │                        ├──────────────────>   │
   │                        │                       ├─ Deduct inventory ✅
   │                        │                       │
   │                        │                       ├─ Emit "inventory.done"
   │                        │                       │

All succeed? Order confirmed! ✅

═════════════════════════════════════════════

If payment fails:
   │
   ├─ Emit "payment.failed"
   │
   ├─────────────────────>  (Service B)
   │
   │ (Service A receives): Payment failed!
   │
   ├─ Compensating transaction: Cancel order
   │
   └─ Order cancelled ✅
```

---

### Orchestration Saga

```
                   ORCHESTRATOR
                       │
         ┌─────────────┼─────────────┐
         ↓             ↓             ↓
     Service A     Service B     Service C

Orchestrator coordinates:
1. Tell A: Create order
   A: Done ✅

2. Tell B: Process payment
   B: Done ✅

3. Tell C: Deduct inventory
   C: Done ✅

All succeeded? Finish!

═════════════════════════════════════════════

If C fails:
   Orchestrator: C failed!

1. Tell B: Cancel payment (compensating transaction)
   B: Done ✅

2. Tell A: Cancel order (compensating transaction)
   A: Done ✅

Rolled back! ✅
```

---

## Part 7: Error Handling in Distributed Transactions 🚨

### Idempotency: Process Once

```
Challenge: If request retried, what happens?

❌ Without Idempotency:
POST /charge?amount=100
Server crashes after charging
Client retries: POST /charge?amount=100
Result: Charged TWICE! 💳💳

✅ With Idempotency:
POST /charge?orderId=123&amount=100 (idempotency key)
Server crashes after charging
Client retries: POST /charge?orderId=123&amount=100
Result: Sees already processed
Returns same result: Charged ONCE ✅
```

**Implementation:**
```java
@Transactional
public PaymentResponse processPayment(String orderId, double amount) {
    // Check if already processed
    PaymentResponse existing = paymentRepository.findByOrderId(orderId);
    if (existing != null) {
        return existing;  // Idempotent! ✅
    }
    
    // Process new payment
    PaymentResponse response = chargeCard(amount);
    paymentRepository.save(response);
    
    return response;
}
```

---

### Retry Logic

```
Retry without thinking = Disaster!

❌ WRONG:
@Transactional
public void transfer() {
    int retries = 0;
    while (retries < 3) {
        try {
            charge();
            return;
        } catch (Exception e) {
            retries++;
            Thread.sleep(1000);
        }
    }
}
// What if charge succeeds but response is lost?
// Retry charges again! DOUBLE CHARGE! 💥

✅ RIGHT:
@Transactional
public void transfer() {
    int retries = 0;
    while (retries < 3) {
        try {
            // Use idempotency key
            response = charge(idempotencyKey);
            return;  // Safe to retry!
        } catch (NetworkException e) {
            retries++;
            if (retries == 3) {
                throw e;
            }
        }
    }
}
```

---

### Deadlock Handling

```
Scenario:
Transaction A: Locks row 1, waits for row 2
Transaction B: Locks row 2, waits for row 1

DEADLOCK! 💀

Detection:
Database throws: DeadlockLoserDataAccessException

Recovery:
@Transactional
public void transfer() throws TransferException {
    int attempts = 0;
    while (attempts < 3) {
        try {
            performTransfer();
            return;  // Success ✅
        } catch (DeadlockLoserDataAccessException e) {
            attempts++;
            if (attempts == 3) {
                throw new TransferException("Deadlock after 3 retries", e);
            }
            Thread.sleep(100 * attempts);  // Exponential backoff
        }
    }
}
```

---

## Part 8: Production Checklist ✅

```
TRANSACTIONAL ANNOTATION ✅
├─ All write operations have @Transactional
├─ Read operations use readOnly=true
├─ Propagation appropriate for nesting
├─ Isolation level matches requirements
├─ Timeout set to prevent hanging
└─ Timeout value reasonable (30-300 seconds)

EXCEPTION HANDLING ✅
├─ All exceptions properly handled
├─ No silent catches
├─ Checked exceptions use rollbackFor
├─ Exception propagates to Spring
├─ Logging in place without eating exceptions
└─ Custom exceptions extend RuntimeException

ACID PROPERTIES ✅
├─ Atomicity: All or nothing operations
├─ Consistency: Database always valid
├─ Isolation: No interference between transactions
└─ Durability: Once committed, permanent

DISTRIBUTED TRANSACTIONS ✅
├─ 2PC or Saga pattern chosen
├─ Idempotency keys in place
├─ Retry logic with backoff
├─ Deadlock handling implemented
├─ Timeout values set for remote calls
└─ Compensation transactions defined

PERFORMANCE ✅
├─ Transactions kept short (< 30 seconds)
├─ No heavy operations in transactions
├─ Read replicas used for read-only
├─ Batch operations where possible
├─ Lock contention monitored
└─ Long-running operations moved outside

MONITORING ✅
├─ Transaction duration tracked
├─ Rollback reasons logged
├─ Deadlock detection in place
├─ Timeout occurrences monitored
├─ Nested transaction depth checked
└─ Database connection pool monitored

TESTING ✅
├─ Happy path tested
├─ Rollback scenarios tested
├─ Exception scenarios tested
├─ Deadlock scenarios tested
├─ Timeout scenarios tested
└─ Load testing completed
```

---

## Part 9: Common Mistakes to Avoid ⚠️

### Mistake 1: Exception Eating
```java
❌ WRONG:
@Transactional
public void transfer() {
    try { charge(); } catch (Exception e) { }
}

✅ CORRECT:
@Transactional
public void transfer() throws PaymentException {
    try { charge(); } catch (Exception e) {
        throw new PaymentException("Failed", e);
    }
}
```

### Mistake 2: Private @Transactional
```java
❌ WRONG:
@Transactional
private void processOrder() { }

✅ CORRECT:
@Transactional
public void processOrder() { }
```

### Mistake 3: Long Transactions
```java
❌ WRONG:
@Transactional
public void processMillionRecords() {
    for (int i = 0; i < 1000000; i++) {
        process(records.get(i));
    }
    // Locks held for hours!
}

✅ CORRECT:
public void processMillionRecords() {
    for (int i = 0; i < 1000000; i++) {
        processSingleRecord(records.get(i));
    }
}

@Transactional
public void processSingleRecord(Record r) {
    // Each record in separate transaction
}
```

### Mistake 4: Checked Exception Without rollbackFor
```java
❌ WRONG:
@Transactional
public void process() throws IOException {
    readFile();  // IOException → COMMIT (wrong!)
}

✅ CORRECT:
@Transactional(rollbackFor = IOException.class)
public void process() throws IOException {
    readFile();  // IOException → ROLLBACK ✅
}
```

### Mistake 5: No Timeout
```java
❌ WRONG:
@Transactional
public void heavyOperation() {
    // Can take unlimited time
    // Locks held forever if crash
}

✅ CORRECT:
@Transactional(timeout = 60)
public void heavyOperation() {
    // Max 60 seconds
    // Fails if takes longer
}
```

---

## Part 10: Real-World Scenarios 🎯

### Scenario 1: Payment Processing with Retry

```java
@Service
public class PaymentService {
    
    @Transactional(rollbackFor = PaymentException.class)
    public PaymentResult processPayment(Order order, String cardToken) 
            throws PaymentException {
        
        int attempts = 0;
        Exception lastError = null;
        
        while (attempts < 3) {
            try {
                // Idempotent payment processing
                PaymentResult result = bankAPI.charge(
                    orderId = order.getId(),  // Idempotency key
                    amount = order.getTotal(),
                    token = cardToken
                );
                
                if (result.isSuccess()) {
                    // Save payment
                    Payment payment = new Payment();
                    payment.setOrderId(order.getId());
                    payment.setAmount(order.getTotal());
                    payment.setStatus("COMPLETED");
                    payment.setTransactionId(result.getTransactionId());
                    paymentRepository.save(payment);
                    
                    return result;
                } else if (result.isRetryable()) {
                    throw new RetryablePaymentException(result.getMessage());
                } else {
                    throw new PaymentException(result.getMessage());
                }
                
            } catch (RetryablePaymentException e) {
                lastError = e;
                attempts++;
                if (attempts < 3) {
                    Thread.sleep(100 * attempts);  // Exponential backoff
                }
            }
        }
        
        throw new PaymentException("Payment failed after 3 attempts", lastError);
    }
}
```

---

### Scenario 2: Saga Pattern Order Processing

```java
@Service
public class OrderOrchestrator {
    
    @Autowired private OrderService orderService;
    @Autowired private PaymentService paymentService;
    @Autowired private InventoryService inventoryService;
    
    public OrderResult processOrder(OrderRequest request) {
        String orderId = UUID.randomUUID().toString();
        
        try {
            // Step 1: Create order
            Order order = orderService.createOrder(request, orderId);
            
            // Step 2: Process payment
            Payment payment = paymentService.processPayment(order, request.getCardToken());
            
            // Step 3: Deduct inventory
            inventoryService.deductStock(order.getItems());
            
            // All succeeded
            orderService.updateOrderStatus(orderId, "CONFIRMED");
            return new OrderResult(orderId, "SUCCESS");
            
        } catch (PaymentException e) {
            // Compensating transaction
            orderService.cancelOrder(orderId);
            return new OrderResult(orderId, "PAYMENT_FAILED");
            
        } catch (InsufficientStockException e) {
            // Compensating transactions
            paymentService.refund(orderId);
            orderService.cancelOrder(orderId);
            return new OrderResult(orderId, "OUT_OF_STOCK");
            
        } catch (Exception e) {
            // Unexpected error
            paymentService.refund(orderId);
            orderService.cancelOrder(orderId);
            return new OrderResult(orderId, "ERROR: " + e.getMessage());
        }
    }
}
```

---

## Summary: You're Now a Transaction Expert! 🦸

### What You Know:
✅ Transactions and ACID properties
✅ @Transactional annotation and all attributes
✅ Propagation types and nesting
✅ Isolation levels and consistency
✅ Exception handling in transactions
✅ Exception eating and prevention
✅ Two-Phase Commit protocol
✅ Saga pattern for distributed transactions
✅ Idempotency and retry logic
✅ Deadlock handling
✅ Production monitoring and checklist
✅ Common mistakes to avoid
✅ Real-world scenarios

### What to Do Next:
1. **Test locally** - Write @Transactional tests
2. **Implement patterns** - Use Saga in your microservices
3. **Monitor** - Track transaction metrics
4. **Learn Distributed Tracing** - Trace across services
5. **Learn Event Sourcing** - Alternative to transactions

---

*You've mastered Transactional Annotation!* 🎉

---

*Last Updated: 2026-10-03*
*Organized for Complete Transactional Mastery! 🎯*
