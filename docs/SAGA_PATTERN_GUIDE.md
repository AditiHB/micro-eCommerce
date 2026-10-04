# Saga Pattern with Compensating Transactions

## Overview

This guide explains the **Saga Pattern** implementation in the micro-eCommerce project, which enables distributed transactions across microservices using compensating transactions for rollback capability.

**Problem Solved**: Traditional ACID transactions don't work across multiple databases in microservices. Sagas provide consistency without distributed locking.

---

## Architecture: Choreography-Based Saga

The implementation uses **Event-Driven Choreography** where services communicate through events and react independently.

```
┌─────────────────────────────────────────────────────────────────┐
│                    SAGA ORCHESTRATION FLOW                      │
└─────────────────────────────────────────────────────────────────┘

HAPPY PATH (Forward Transactions):
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

  Order Service          Inventory Service        Payment Service
       │                      │                         │
       │ 1. Create Order      │                         │
       ├─────────────────────>│                         │
       │                      │ 2. Reserve Stock       │
       │                      │                         │
       │                 <InventoryReservedEvent>       │
       │<───────────────────────────────────────────────┤
       │                                                │
       │                      │          3. Process Payment
       │                      │<────────────────────────│
       │                      │                 <PaymentProcessedEvent>
       │<─────────────────────────────────────────────┤
       │                                                │
       ├─ Update Status: COMPLETED                      │
       │                                                │

FAILURE PATH (Compensating Transactions):
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Scenario: Payment Processing Fails
──────────────────────────────────────────────────────────────────

  Order Service          Inventory Service        Payment Service
       │                      │                         │
       │ OrderCreatedEvent    │                         │
       ├─────────────────────>│                         │
       │                  [Reserve Stock]               │
       │         InventoryReservedEvent                 │
       │<─────────────────────────────────────────────┬─┤
       │                                                │
       │                      │                  [ERROR: Payment Failed]
       │                      │                         │
       │                      │      <PaymentFailedEvent>
       │<─────────────────────────────────────────────┤
       │                                                │
       ├─ Publish: OrderCancelledEvent                 │
       │                                                │
       │                      │  <OrderCancelledEvent>  │
       │                      ├────────────────────────>│
       │                      │                  [Refund Payment]
       │                      │                         │
       │                      │ [Release Stock]    <RefundCompletedEvent>
       │                      │<────────────────────────┤
       │                                                │
       ├─ Update Status: CANCELLED                     │
```

---

## Events and Compensating Transactions

### Forward Transaction Events

| Event | Publisher | Subscriber | Action |
|-------|-----------|-----------|--------|
| `OrderCreatedEvent` | Order Service | Inventory Service | Reserve inventory stock |
| `InventoryReservedEvent` | Inventory Service | Payment Service | Process payment |
| `PaymentProcessedEvent` | Payment Service | Order Service | Mark order COMPLETED |

### Compensating Transaction Events

| Event | Trigger | Publisher | Compensation |
|-------|---------|-----------|--------------|
| `PaymentFailedEvent` | Payment processing exception | Payment Service | Triggers inventory release |
| `OrderCancelledEvent` | Inventory or payment failure | Order Service | Triggers payment refund |
| `InventoryReleasedEvent` | Payment failed | Inventory Service | Stock returned to inventory |
| `RefundCompletedEvent` | Order cancelled | Payment Service | Payment reversed, order fully cancelled |

---

## Code Implementation

### 1. New Event Classes

#### InventoryReleasedEvent.java
```java
/**
 * Compensating Transaction: Release reserved inventory.
 * Triggered when payment fails, returning stock to available inventory.
 */
public class InventoryReleasedEvent extends DomainEvent {
    private Long orderId;
    private String productId;
    private Integer quantity;
    // ... getters/setters
}
```

#### RefundInitiatedEvent.java
```java
/**
 * Compensating Transaction: Initiate refund.
 * Triggered when order is cancelled.
 */
public class RefundInitiatedEvent extends DomainEvent {
    private Long orderId;
    private Long paymentId;
    private BigDecimal refundAmount;
    private String reason;
    // ... getters/setters
}
```

#### RefundCompletedEvent.java
```java
/**
 * Compensation Complete: Refund processed.
 * Published after refund is successfully applied.
 */
public class RefundCompletedEvent extends DomainEvent {
    private Long orderId;
    private Long paymentId;
    private BigDecimal refundAmount;
    // ... getters/setters
}
```

#### OrderCancelledEvent.java
```java
/**
 * Triggers Compensation: Order cancelled.
 * Published when inventory or payment fails.
 */
public class OrderCancelledEvent extends DomainEvent {
    private Long orderId;
    private String reason;
    // ... getters/setters
}
```

### 2. Enhanced Event Classes

**InventoryReservedEvent** - Now includes product/quantity for compensation:
```java
public InventoryReservedEvent(Long orderId, String productId, Integer quantity) {
    // ... enables inventory release without additional lookups
}
```

**PaymentFailedEvent** - Now includes inventory details:
```java
public PaymentFailedEvent(Long orderId, String productId, Integer quantity, String reason) {
    // ... enables inventory service to release without extra queries
}
```

### 3. Event Listener Updates

#### InventoryEventListener - Compensating Transaction
```java
/**
 * Compensating Transaction: Release inventory on payment failure.
 * Reverses the inventory reservation when payment processing fails.
 */
@KafkaListener(topics = "payment-failed", groupId = "inventory-group")
public void handlePaymentFailed(@Payload PaymentFailedEvent event, Acknowledgment ack) {
    // 1. Find inventory
    // 2. Add back the reserved quantity (compensate the reserve action)
    // 3. Publish InventoryReleasedEvent
    // 4. Acknowledge message
}
```

#### PaymentEventListener - Compensating Transaction
```java
/**
 * Compensating Transaction: Refund payment on order cancellation.
 * Reverses the payment charge when order is cancelled.
 */
@KafkaListener(topics = "order-cancelled", groupId = "payment-group")
public void handleOrderCancelled(@Payload OrderCancelledEvent event, Acknowledgment ack) {
    // 1. Find payment by order ID
    // 2. Change status to REFUNDED (compensate the charge)
    // 3. Publish RefundCompletedEvent
    // 4. Acknowledge message
}
```

#### OrderEventListener - Saga Orchestrator
```java
/**
 * Receives failure events and publishes OrderCancelledEvent
 * to trigger the compensating transaction chain.
 */
@KafkaListener(topics = "payment-failed", groupId = "order-group")
public void handlePaymentFailed(@Payload PaymentFailedEvent event, Acknowledgment ack) {
    // 1. Mark order as CANCELLED
    // 2. Publish OrderCancelledEvent (triggers compensation)
}
```

---

## Example: Complete Saga Flow

### Scenario: Order Saga with Payment Failure

**Step 1: Customer Creates Order**
```
POST /api/orders
OrderService creates Order and publishes OrderCreatedEvent
  - orderId: 123
  - productId: "PROD-456"
  - quantity: 2
```

**Step 2: Inventory Service Reserves Stock** (Forward Transaction)
```
Receives: OrderCreatedEvent
Action: Reduce inventory by 2 units
Publishes: InventoryReservedEvent(123, "PROD-456", 2)
```

**Step 3: Payment Service Processes Payment** (Forward Transaction)
```
Receives: InventoryReservedEvent
Action: Create Payment record, charge customer
Exception: Credit card declined!
Publishes: PaymentFailedEvent(123, "PROD-456", 2, "Card declined")
```

**Step 4: Inventory Service Releases Stock** (Compensating Transaction)
```
Receives: PaymentFailedEvent
Action: Add 2 units back to inventory (UNDO step 2)
Publishes: InventoryReleasedEvent(123, "PROD-456", 2)
```

**Step 5: Order Service Publishes Cancellation** (Orchestration)
```
Receives: PaymentFailedEvent
Action: Mark order as CANCELLED
Publishes: OrderCancelledEvent(123, "Card declined")
```

**Step 6: Payment Service Refunds** (Compensating Transaction)
```
Receives: OrderCancelledEvent
Action: Mark Payment as REFUNDED (UNDO step 3)
Publishes: RefundCompletedEvent(123, paymentId, amount)
```

**Result**: 
- ✓ Inventory returned to 2 units (compensated)
- ✓ Payment refunded to customer (compensated)
- ✓ Order marked CANCELLED (saga complete)
- System is in consistent state despite failure

---

## Key Design Principles

### 1. **Idempotency**
All event handlers are idempotent - processing the same event twice produces the same result:
```java
// ✓ GOOD: Idempotent - same result regardless of how many times executed
payment.setStatus(PaymentStatus.REFUNDED);
repository.save(payment);

// ✗ BAD: Non-idempotent - refunds multiple times if message retried
refundAmount = calculateRefund();
chargeAccount(-refundAmount);
```

### 2. **Consistency Eventually**
- Services don't have real-time data
- Consistency achieved through event propagation
- Acceptable for business scenarios where eventual consistency is OK

### 3. **Failure Visibility**
All failures trigger visible compensating transactions:
- No silent failures
- Every failure logs compensation actions
- Audit trail of all compensation attempts

### 4. **Dead Letter Queue (DLQ)**
Events that fail multiple times go to DLQ for manual intervention:
```
event.dlq topic: Used for messages that cannot be processed
```

---

## Testing Compensating Transactions

### Test 1: Verify Inventory Release
```java
@Test
public void testInventoryReleaseOnPaymentFailure() {
    // Arrange: Create order with 5 units
    // Act: Trigger payment failure
    // Assert: 
    //   - Inventory quantity increased by 5
    //   - InventoryReleasedEvent published
}
```

### Test 2: Verify Payment Refund
```java
@Test
public void testPaymentRefundOnOrderCancellation() {
    // Arrange: Create payment with amount $99.99
    // Act: Publish OrderCancelledEvent
    // Assert:
    //   - Payment status changed to REFUNDED
    //   - RefundCompletedEvent published
}
```

### Test 3: Verify Complete Saga Rollback
```java
@Test
public void testCompleteSagaRollback() {
    // Arrange: Order with reserved inventory and processed payment
    // Act: Trigger payment failure
    // Assert:
    //   - Order cancelled
    //   - Inventory restored
    //   - Payment refunded
    //   - All compensation events published
}
```

---

## Monitoring Compensating Transactions

### Metrics to Track
```
# Forward transactions
ecommerce.orders.created
ecommerce.payments.processed
ecommerce.inventory.reserved

# Compensating transactions
ecommerce.orders.cancelled
ecommerce.payments.refunded
ecommerce.inventory.released

# Failure rates
ecommerce.saga.failures
ecommerce.saga.compensations_triggered
```

### Prometheus Queries
```promql
# Success rate of sagas
rate(ecommerce.orders.completed[5m]) / rate(ecommerce.orders.created[5m])

# Compensation rate
rate(ecommerce.saga.compensations_triggered[5m])

# Payment refund success
rate(ecommerce.payments.refunded[5m]) / rate(ecommerce.saga.compensations_triggered[5m])
```

### Log Patterns
```
✓ Order successfully COMPLETED
✗ Order CANCELLED due to inventory failure
✓ Inventory released successfully for order
✓ Payment refunded successfully for order
✓ Saga COMPENSATED for order
```

---

## Limitations & Future Improvements

### Current Limitations
1. **No Orchestrator**: Choreography can become complex with many services
2. **Eventual Consistency**: Data is not immediately consistent
3. **Manual Compensation for Unplanned Failures**: If compensation itself fails, manual intervention needed
4. **Limited Visibility**: Hard to see overall saga state without tracing

### Future Improvements (Phase 14+)
1. **Saga Orchestrator Service**: Centralized state machine for complex sagas
2. **Distributed Tracing**: Full X-Trace-ID correlation across compensation
3. **Compensation Retry Logic**: Automatic retry with exponential backoff
4. **Saga State Management**: Persistent saga state for recovery
5. **Dashboards**: Real-time saga status and compensation metrics

---

## Related Documentation

- **ARCHITECTURE.md** - See "Saga Pattern (Distributed Transactions)" section
- **CONCEPTS_EXPLAINED.md** - See "Saga Pattern for Distributed Transactions" section
- **SETUP_AND_DEPLOYMENT.md** - Monitoring and testing sections

---

## Summary

The Saga Pattern implementation provides:
✓ Distributed transactions without distributed locking
✓ Compensating transactions for consistent rollback
✓ Event-driven choreography for loose coupling
✓ Eventual consistency for microservices
✓ Complete audit trail of all transactions and compensations
