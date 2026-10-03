# Transactional Annotation Visual Guide 🎨

## Basic Transaction Flow

```
TRANSACTION WITHOUT ERRORS (Success Path)
═══════════════════════════════════════════

User Action: Transfer $50

┌─────────────────────────────────────────┐
│ @Transactional                          │
│ public void transfer()                  │
└──────────────────┬──────────────────────┘
                   ↓
        ┌──────────────────────┐
        │ START TRANSACTION    │
        │ (Pending)            │
        └──────────┬───────────┘
                   ↓
        ┌──────────────────────┐
        │ Debit Account        │
        │ $100 → $50 (pending) │
        └──────────┬───────────┘
                   ↓
        ┌──────────────────────┐
        │ Credit Account       │
        │ $50 → $100 (pending) │
        └──────────┬───────────┘
                   ↓
        ┌──────────────────────┐
        │ Method Ends OK ✅    │
        └──────────┬───────────┘
                   ↓
        ┌──────────────────────┐
        │ COMMIT ✅            │
        │ Changes saved!       │
        └──────────┬───────────┘
                   ↓
        DATABASE STATE:
        ├─ Account 1: $50 ✅
        └─ Account 2: $100 ✅
```

---

## Transaction With Exception (Failure Path)

```
TRANSACTION WITH EXCEPTION (Rollback)
═══════════════════════════════════════

┌─────────────────────────────────────────┐
│ @Transactional                          │
│ public void transfer()                  │
└──────────────────┬──────────────────────┘
                   ↓
        ┌──────────────────────┐
        │ START TRANSACTION    │
        │ (Pending)            │
        └──────────┬───────────┘
                   ↓
        ┌──────────────────────┐
        │ Debit Account        │
        │ $100 → $50 (pending) │
        └──────────┬───────────┘
                   ↓
        ┌──────────────────────┐
        │ Credit Account       │
        │ ERROR! ❌            │
        │ SQLException thrown  │
        └──────────┬───────────┘
                   ↓
        ┌──────────────────────┐
        │ EXCEPTION CAUGHT! 💥 │
        └──────────┬───────────┘
                   ↓
        ┌──────────────────────┐
        │ ROLLBACK ❌          │
        │ Undo all changes     │
        └──────────┬───────────┘
                   ↓
        DATABASE STATE:
        ├─ Account 1: $100 ✅ (reverted)
        └─ Account 2: $50 ✅ (reverted)
```

---

## Exception Handling Comparison

```
UNCHECKED EXCEPTION (ArithmeticException)
────────────────────────────────────────────

@Transactional
public void transfer() {
    debit();           // ✅
    int x = 1/0;       // ❌ ArithmeticException!
    credit();          // ⏸️ Never runs
}

Result: ROLLBACK! ✅

═════════════════════════════════════════════

CHECKED EXCEPTION (IOException)
────────────────────────────────────────────

@Transactional
public void transfer() throws IOException {
    debit();           // ✅
    throw new IOException();  // ❌ IOException!
    credit();          // ⏸️ Never runs
}

Result: COMMIT! 😱 (This is BAD!)

═════════════════════════════════════════════

CHECKED EXCEPTION WITH rollbackFor
────────────────────────────────────────────

@Transactional(rollbackFor = IOException.class)
public void transfer() throws IOException {
    debit();           // ✅
    throw new IOException();  // ❌ IOException!
    credit();          // ⏸️ Never runs
}

Result: ROLLBACK! ✅ (This is GOOD!)
```

---

## Propagation Behavior

```
PROPAGATION.REQUIRED (Default)
═══════════════════════════════════════════

Service A                Service B
────────────────────────────────────────

@Transactional           @Transactional(propagation=REQUIRED)
transferA() {            transferB() {
    op1 ✅                   op2 ✅
    ├─ calls transferB()
    │  ├─ op2 ✅ (SAME TX!)
    │  ├─ error ❌ (SAME TX!)
    │  └─ rollbacks
    └─ op3 ROLLS BACK TOO!
}

Result: All operations rollback together!


PROPAGATION.REQUIRES_NEW
═══════════════════════════════════════════

Service A                Service B
────────────────────────────────────────

@Transactional           @Transactional(propagation=REQUIRES_NEW)
transferA() {            transferB() {
    op1 ✅                   op2 ✅
    ├─ calls transferB()
    │  ├─ NEW TX START!
    │  ├─ op2 ✅ (NEW TX!)
    │  ├─ error ❌ (NEW TX!)
    │  ├─ COMMIT/ROLLBACK (NEW TX)
    │  └─ returns to A
    └─ op3 ✅ (continues regardless!)
}

Result: B's transaction independent from A!


PROPAGATION.SUPPORTS
═══════════════════════════════════════════

Service A                Service B
────────────────────────────────────────

@Transactional           @Transactional(propagation=SUPPORTS)
transferA() {            transferB() {
    op1 ✅                   op2 ✅ (IN A's TX)
    ├─ calls transferB()
    │  └─ op2 (uses existing TX)
    └─ op3 ✅
}

Result: Uses A's transaction if available, else none!


PROPAGATION.NOT_SUPPORTED
═══════════════════════════════════════════

Service A                Service B
────────────────────────────────────────

@Transactional           @Transactional(propagation=NOT_SUPPORTED)
transferA() {            transferB() {
    op1 ✅ (in TX)            op2 ✅ (NO TX!)
    ├─ calls transferB()
    │  └─ A's TX SUSPENDED!
    │     op2 runs without TX
    │     Returns to A
    └─ op3 ✅ (TX resumed)
}

Result: B runs outside transaction!
```

---

## Two-Phase Commit (2PC) Flow

```
DISTRIBUTED TRANSACTION ACROSS 2 DATABASES
═════════════════════════════════════════════

         COORDINATOR (Master)
                  │
         ┌────────┴────────┐
         ↓                  ↓
    ┌─────────┐        ┌─────────┐
    │ Bank DB │        │ Insure DB
    └─────────┘        └─────────┘

PHASE 1: PREPARE (Can you do it?)
═════════════════════════════════════

  Coordinator: "Bank, prepare to debit $50"
  Bank: "OK, I can do it (locked)"

  Coordinator: "Insurance, prepare to add $50"
  Insurance: "OK, I can do it (locked)"

         ┌────────────────────┐
         │ BOTH PREPARED! ✅  │
         └────────┬───────────┘
                  ↓

PHASE 2: COMMIT (Do it!)
═════════════════════════════════

  Coordinator: "Bank, commit!"
  Bank: COMMITS $50 ✅

  Coordinator: "Insurance, commit!"
  Insurance: COMMITS $50 ✅

         ┌────────────────────┐
         │ BOTH COMMITTED! ✅ │
         │ DONE! 🎉           │
         └────────────────────┘

═════════════════════════════════

FAILURE SCENARIO (Can't do it?)
═════════════════════════════════

  Coordinator: "Bank, prepare to debit $50"
  Bank: "OK, I can do it (locked)"

  Coordinator: "Insurance, prepare to add $50"
  Insurance: "NO! I can't. Limit exceeded! ❌"

         ┌────────────────────┐
         │ INSURANCE FAILED! ❌│
         └────────┬───────────┘
                  ↓

  Coordinator: "Bank, rollback!"
  Bank: ROLLBACKS ❌ (unlock)

  Coordinator: "Insurance, rollback!"
  Insurance: ROLLBACKS ❌ (unlock)

         ┌────────────────────┐
         │ BOTH ROLLED BACK! ❌│
         │ Money safe! ✅      │
         └────────────────────┘
```

---

## Exception Eating (What Goes Wrong)

```
CORRECT: Exception Re-thrown
═══════════════════════════════════

@Transactional
public void transfer() {
    try {
        debit();      // ✅
        credit();     // ❌ Exception!
    } catch (Exception e) {
        throw e;      // ✅ RE-THROW!
    }
}

Spring sees:        EXCEPTION PROPAGATED
Spring does:        ROLLBACK! ✅

Result: ✅ CORRECT


WRONG: Exception Swallowed
═══════════════════════════════════

@Transactional
public void transfer() {
    try {
        debit();      // ✅
        credit();     // ❌ Exception!
    } catch (Exception e) {
        // Silent catch! 🤐
    }
}

Spring sees:        NO EXCEPTION
Spring does:        COMMIT! (WRONG!)

Result: ❌ INCORRECT
├─ Debit happened ✅
├─ Credit didn't happen ❌
└─ But Spring committed!

Database state: INCONSISTENT! 💥


CORRECT: Log and Re-throw
═══════════════════════════════════

@Transactional
public void transfer() {
    try {
        debit();      // ✅
        credit();     // ❌ Exception!
    } catch (Exception e) {
        logger.error("Credit failed", e);
        throw new RuntimeException("Transfer failed", e);
    }
}

Spring sees:        EXCEPTION PROPAGATED
Spring does:        ROLLBACK! ✅

Result: ✅ CORRECT
```

---

## Transaction Isolation Levels

```
ISOLATION LEVEL: Dirty Read Scenario
═════════════════════════════════════

Transaction A: Transfer $50
Transaction B: Check balance

DIRTY READ (No isolation)
─────────────────────────
Time  A: Debit $50 (pending)
      B: Sees $50 (DIRTY! Not committed!)
      A: ROLLBACK ❌
      B: Now balance is WRONG! 💥

READ_UNCOMMITTED allows this ❌

─────────────────────────────

COMMITTED READ (Better)
─────────────────────────
Time  A: Debit $50 (pending)
      B: Sees $100 (original, safe)
      A: COMMIT ✅
      B: Now sees $50 (updated, correct!) ✅

READ_COMMITTED prevents dirty reads ✅

─────────────────────────────

REPEATABLE READ (Better still)
─────────────────────────────
Time  A: Start, see $100
      B: Debit $50
      B: COMMIT ✅
      A: See $100 again (SAME SNAPSHOT!)
      
Can see inconsistency but never dirty data

REPEATABLE_READ prevents non-repeatable reads ✅

─────────────────────────────

SERIALIZABLE (Strongest)
─────────────────────────
Time  A: Start, lock table
      B: Waits... (A's transaction first)
      A: COMMIT, unlock
      B: Now proceeds

Behaves as if transactions ran one after another

SERIALIZABLE prevents all anomalies ✅
```

---

## Order Processing Flow

```
@Transactional
placeOrder(orderId, items, payment) {
    ┌───────────────────────────────────┐
    │ TRANSACTION STARTS                │
    └───────────────────┬───────────────┘
                        ↓
    ┌───────────────────────────────────┐
    │ Create Order                      │
    │ INSERT into orders table ✅       │
    │ (pending)                         │
    └───────────────────┬───────────────┘
                        ↓
    ┌───────────────────────────────────┐
    │ Update Inventory                  │
    │ UPDATE inventory SET qty = qty-1  │
    │ (pending)                         │
    └───────────────────┬───────────────┘
                        ↓
    ┌───────────────────────────────────┐
    │ Process Payment                   │
    │ POST /charge ✅                   │
    │ (pending)                         │
    └───────────────────┬───────────────┘
                        ↓
    ┌───────────────────────────────────┐
    │ Send Email                        │
    │ (pending)                         │
    └───────────────────┬───────────────┘
                        ↓
    ┌───────────────────────────────────┐
    │ Method Ends Successfully ✅       │
    └───────────────────┬───────────────┘
                        ↓
    ┌───────────────────────────────────┐
    │ COMMIT ALL CHANGES ✅             │
    │ ├─ Order created                  │
    │ ├─ Inventory updated              │
    │ ├─ Payment processed              │
    │ └─ Email sent                     │
    └───────────────────────────────────┘


─────────────────────────────────────────

IF PAYMENT FAILS:

    ┌───────────────────────────────────┐
    │ Create Order ✅                   │
    │ Update Inventory ✅               │
    │ Process Payment ❌ (Exception!)   │
    │ Send Email (never reached)        │
    └───────────────────┬───────────────┘
                        ↓
    ┌───────────────────────────────────┐
    │ EXCEPTION DETECTED! 💥            │
    └───────────────────┬───────────────┘
                        ↓
    ┌───────────────────────────────────┐
    │ ROLLBACK ENTIRE TRANSACTION! ❌  │
    │ ├─ Order creation reverted        │
    │ ├─ Inventory restoration          │
    │ └─ NO email sent                  │
    └───────────────────────────────────┘

    Result: Clean state, no inconsistency! ✅
```

---

## Attribute Combinations Matrix

```
@Transactional ATTRIBUTE COMBINATIONS
═══════════════════════════════════════

┌──────────────┬─────────────┬────────────┐
│ Propagation  │ Isolation   │ Timeout    │
├──────────────┼─────────────┼────────────┤
│ REQUIRED     │ DEFAULT     │ -1         │
│ REQUIRES_NEW │ DIRTY_READ  │ 30s        │
│ SUPPORTS     │ COMMITTED   │ 60s        │
│ NOT_SUPPORT. │ REPEATABLE  │ timeout-ex │
│ MANDATORY    │ SERIALIZABLE│ custom     │
│ NEVER        │             │            │
│ NESTED       │             │            │
└──────────────┴─────────────┴────────────┘

PROPAGATION × ISOLATION × TIMEOUT × READONLY
= Thousands of combinations!

Common combinations:

1. @Transactional
   (REQUIRED, DEFAULT, -1, false) - Most common

2. @Transactional(readOnly=true)
   (REQUIRED, DEFAULT, -1, true) - Read operations

3. @Transactional(propagation=REQUIRES_NEW)
   (REQUIRES_NEW, DEFAULT, -1, false) - Independent

4. @Transactional(isolation=SERIALIZABLE)
   (REQUIRED, SERIALIZABLE, -1, false) - Strict
```

---

## Rollback Behavior Summary

```
┌──────────────────┬────────────┬──────────────┐
│ Exception Type   │ By Default │ With Config  │
├──────────────────┼────────────┼──────────────┤
│ RuntimeException │ ROLLBACK   │ COMMIT       │
│                  │ ✅         │ ❌ avoid     │
│ (Unchecked)      │            │ rollbackFor  │
├──────────────────┼────────────┼──────────────┤
│ IOException      │ COMMIT     │ ROLLBACK     │
│ SQLException     │ ❌ avoid   │ ✅ use       │
│ (Checked)        │            │ rollbackFor  │
├──────────────────┼────────────┼──────────────┤
│ CustomException  │ ROLLBACK   │ COMMIT       │
│ extends          │ ✅         │ ❌ avoid     │
│ RuntimeException │            │              │
└──────────────────┴────────────┴──────────────┘

RECOMMENDATION:
├─ Use RuntimeException for errors that should rollback
├─ Use rollbackFor for checked exceptions
└─ Re-throw all exceptions (never swallow!)
```

---

*Last Updated: 2026-10-03*
*Visual Guide for Clear Understanding! 🎨*
