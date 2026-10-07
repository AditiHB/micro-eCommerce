# Transactions

### Q: What does `@Transactional`'s default propagation (`REQUIRED`) actually mean, and where does this repo
rely on a *non-default* propagation deliberately?

**A:** `REQUIRED` (the default) means "join the caller's transaction if one is already open on this thread;
otherwise start a new one." Most `@Transactional` methods in this repo rely on exactly that default — each
call from a fresh request/listener thread simply starts its own transaction.

The deliberate exception is `Propagation.MANDATORY`, used on `ReservationService`
(`@Transactional(propagation = Propagation.MANDATORY)`, class-level) and on `EventPublisher.publish()`. `MANDATORY`
means "there must *already* be an open transaction on this thread, or throw" — it's a guard, not a convenience.
The reason: `ReservationService.reserve()`'s correctness depends on running inside the *caller's* transaction
(the saga handler's), so that the stock reservation, the inbox's processed-event marker, and the event
announcing it all commit or roll back together as one atomic unit. If `ReservationService` silently started its
*own* transaction instead (as `REQUIRED` would allow), you could end up with stock reserved but the event never
actually published, or vice versa — exactly the inconsistency the outbox pattern exists to prevent (see
[microservices/05](../microservices/05-event-driven-architecture.md)). `MANDATORY` makes that assumption
impossible to violate silently: if some future caller ever invoked `reserve()` without an open transaction, it
fails loudly and immediately, instead of quietly running in its own transaction and reintroducing the exact bug
the design was meant to prevent.

### Q: By default, does a `@Transactional` method roll back on *any* exception?

**A:** No — this trips people up. Spring's default rollback rule is: roll back on **unchecked** exceptions
(`RuntimeException` and `Error`), but **not** on checked exceptions, unless you explicitly declare
`@Transactional(rollbackFor = ...)`. Every custom exception type in this repo's hierarchy
(`BusinessException`, `ConflictException`, `ResourceNotFoundException`, `DependencyUnavailableException`, etc.)
is an unchecked `RuntimeException` — which is precisely what makes the default rollback behavior "just work"
without a single `rollbackFor` annotation needed anywhere in the codebase. If this repo had modeled business
failures as checked exceptions instead, every one of those `@Transactional` methods would need an explicit
`rollbackFor` to behave correctly — a subtle, easy-to-miss source of "the exception was thrown but the data got
committed anyway" bugs.

### Q: Describe a real transaction-scope mistake this repo's own code review surfaced, and why it matters.

**A:** `PaymentSagaHandler`/`PaymentService` are `@Transactional` at the class level, and `charge()`/`refund()`
call the payment gateway (`gateway.authorize(...)`, `gateway.capture(...)`) **inside** that transaction
boundary. Today this is harmless — `SimulatedPaymentGateway` does no real I/O — but the transaction boundary is
drawn around the external-processor call *by construction*. The moment a real payment gateway (a genuine
network call to a third-party processor) is wired in, every charge and refund would hold a database connection
and whatever row locks it's taken open for the **full duration of that network call**, directly serializing the
connection pool's availability against third-party processor latency — a slow or degraded payment processor
would start starving the connection pool for completely unrelated requests, not just payment ones.

The fix isn't exotic: call the gateway *outside* the transaction (or in its own short transaction for just the
local state mutation), and persist the outcome in a brief follow-up transaction — never hold a DB transaction
open across a call whose latency you don't control.

### Q: What's the default transaction isolation level, and has this repo ever needed to change it?

**A:** Spring's default is `Isolation.DEFAULT` — defer entirely to whatever the underlying database's own
default is, which for Postgres is `READ COMMITTED`. This repo never overrides isolation level anywhere
(no `isolation = Isolation....` found on any `@Transactional` annotation) — every correctness guarantee that
might otherwise need a stricter isolation level (preventing a lost update on concurrent stock decrements, for
instance) is instead achieved through **explicit row locking** (`SELECT ... FOR UPDATE`) and **atomic
conditional updates** (`WHERE quantity >= :n`), not by raising the isolation level globally. That's a
deliberate, narrower tool: raising isolation to `SERIALIZABLE` globally would protect every transaction at the
cost of throughput and added retry-on-conflict handling everywhere; locking only the specific rows that
actually need it protects exactly the operation that needs protecting without paying that cost for every other
read in the system.

### Q: Why can calling an `@Transactional` method from another method *on the same bean* silently not
start a new transaction, even though the annotation is right there on the method?

**A:** Same root cause as the `@Async` self-invocation gotcha covered in the microservices guide
(see [microservices/08](../microservices/08-async-processing-and-thread-safety.md)) and in
[07 of this guide](07-aop-proxies-and-cross-cutting-concerns.md): `@Transactional` is implemented as a Spring
AOP proxy, and a call from within the same class bypasses that proxy entirely, running as a plain, unintercepted
Java method call. A method carrying its *own* `@Transactional` (say, `REQUIRES_NEW`, to force a fresh
transaction) would **not** get that behavior if invoked internally via `this.otherMethod()` — the proxy that
would normally suspend the outer transaction and start a new one is simply never consulted.

This repo actually shows the harmless version of the same mechanism, worth contrasting: `OrderService.expire(...)`
is `public`, inherits the class-level `@Transactional` (line 48), and is called from `OrderSagaDeadline` — a
*different* bean — so that outer call genuinely does go through the proxy and opens a transaction. `expire(...)`
then calls the package-private `cancel(...)` internally, which has no `@Transactional` of its own to begin
with — there's nothing for self-invocation to silently skip here, because `cancel(...)` was always meant to run
*inside* whatever transaction its caller already opened, not to start one independently. The bug only appears
when a method's *own* declared transactional behavior (a different propagation, in particular) is the thing
being bypassed by an internal call — which is exactly why `ReservationService`'s `MANDATORY` propagation,
discussed above, is enforced at the method a *different bean* calls into, never via a same-class internal call.
