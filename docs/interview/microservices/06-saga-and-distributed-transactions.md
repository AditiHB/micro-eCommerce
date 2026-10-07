# Saga & Distributed Transactions

### Q: Why can't you just wrap an order placement in one transaction across order, inventory, and payment
services?

**A:** Because each owns its own database (see [01](01-architecture-and-service-boundaries.md)), and a
classic ACID transaction can't span independent databases without something like two-phase commit (2PC/XA) —
which requires every participant to vote and hold locks open until *all* participants agree, across network
calls, for however long the slowest one takes. That serializes throughput across service boundaries and turns
any one participant's downtime into everyone's downtime; it's also simply not supported by Kafka or most modern
datastores. The saga pattern is the standard answer: give up atomicity for **eventual consistency**, modeled
as a sequence of local transactions, each with an explicit way to undo its effect if a later step fails.

### Q: Choreography vs. orchestration sagas — which does this repo use, and what's the tradeoff?

**A:** **Choreography** — there's no central saga orchestrator class telling every service what to do next.
Instead, each service reacts to events and emits its own: `order-service` creates an order and emits
`order.created`; `inventory-service` consumes that, reserves stock, and emits `inventory.reserved` (or
`inventory.reservation-failed`); `payment-service` consumes `inventory.reserved` and emits `payment.completed`
or `payment.failed`; `notification-service` consumes the terminal events and notifies the customer. Each
service only needs to know the shape of the events it consumes and emits — not the existence of any other
specific service.

The tradeoff: with no central coordinator, there's no single place to read "what's the overall saga logic" —
it's implicit in which service reacts to which event, spread across all of them. Orchestration (a dedicated
saga-orchestrator service issuing explicit commands and waiting for replies) centralizes that logic, which
is easier to reason about for a complex saga but reintroduces a central coordinator as a dependency every step
needs to be reachable from.

### Q: Walk through what happens when inventory reservation *fails* partway through the saga.

**A:** `inventory-service` emits `inventory.reservation-failed` (with a reason) instead of
`inventory.reserved`. `order-service` consumes that and transitions the order to a cancelled/failed terminal
state — no payment is ever attempted, because `payment-service` never saw a success event to react to. If
failure happens *after* payment already succeeded (e.g. a later step fails), the earlier services run their
**compensating actions**: `payment-service` issues a refund in reaction to an `order.cancelled` event, exactly
mirroring the original charge but undoing it. Every compensation is itself a normal, event-driven, idempotent
operation — not a special rollback mechanism bolted onto the saga.

### Q: What stops a saga from getting stuck forever if a message is silently lost?

**A:** `OrderSagaDeadline` — described in its own javadoc as "the saga's safety net." A scheduled job
(`cancelStaleOrders()`, every `reaper-interval`, default 30s) finds orders that have been open longer than
`saga-timeout` and cancels them, which triggers the same compensation path a normal failure would. Without
this, a lost event (a message stuck in a dead-letter queue, a service down too long) could leave an order
`PENDING` forever, holding stock and possibly a charge indefinitely.

This reaper is itself safe to run on every replica simultaneously: each order is expired in its own transaction
under optimistic locking, so if two replicas pick the same stale order, one simply loses the race
(`OptimisticLockingFailureException`, caught and logged, not an error). This session also parallelized the
reaper's batch of expirations with `CompletableFuture` + a dedicated bounded executor, precisely *because*
each expiry was already designed to be safe under concurrent execution — see
[08](08-async-processing-and-thread-safety.md).

### Q: Why does an event consumer need to check for duplicates if Kafka already guarantees delivery?

**A:** Kafka guarantees **at-least-once** delivery by default, not exactly-once, when combined with the
outbox pattern's own at-least-once semantics (a crash between "Kafka acked" and "mark outbox row published"
means the same row gets sent again next poll). So a saga step's consumer *will* occasionally see the same
event twice, and must be idempotent about it — `ReservationService.reserve()` checks
`reservationRepository.existsByOrderId(orderId)` first and treats a replay as a no-op ("stock was already taken
for this order"), rather than reserving the same stock twice for the same order.

### Q: Is the saga pattern "distributed transactions," or something different?

**A:** Different, and this distinction is worth stating precisely in an interview: a distributed transaction
(2PC) gives you atomicity and isolation across services — either everything commits or nothing does, and
nobody sees an intermediate state. A saga gives you neither of those guarantees in the strict ACID sense: an
order genuinely exists, visible to reads, before payment has been attempted; if payment fails, there was a real
window where the system's state was "inconsistent" by strict-transaction standards. What a saga *does*
guarantee is that the system always reaches a consistent **end** state — through forward progress or through
compensation — even though it passes through valid-but-incomplete intermediate states to get there. That's
why it's called eventual consistency, not just "slower consistency."
