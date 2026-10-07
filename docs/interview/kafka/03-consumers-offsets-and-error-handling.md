# Consumers, Offsets & Error Handling

### Q: Auto-commit or manual ack — which does this repo use, and why does it matter for at-least-once
delivery?

**A:** Manual, per-record acknowledgment: `ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG = false`
(`consumerFactory()`), combined with `ContainerProperties.AckMode.RECORD` on the listener container factory —
Spring Kafka commits the offset **only after the listener method returns successfully**, one record at a time,
never on a timer regardless of whether processing actually finished.

This is the mechanical reason this system gets **at-least-once**, not at-most-once, delivery. With
auto-commit, the offset could advance on a timer *before* a slow listener finishes processing a record — a
crash in that window would lose the message entirely (the broker thinks it was already consumed, even though
the handler never actually ran). With manual, post-processing commit, the worst case that can happen is the
**same record being redelivered** after a crash (processed, but the commit itself didn't make it to the broker
in time) — never a message silently skipped. That's exactly why consumers in this system have to be idempotent
(the inbox pattern — see [../microservices/06](../microservices/06-saga-and-distributed-transactions.md)):
manual-ack-after-processing trades "might process twice" for "will never lose a message," and idempotent
consumers are what makes "might process twice" harmless.

### Q: What does `ISOLATION_LEVEL_CONFIG = "read_committed"` do, and why does it matter here even though
this repo doesn't use Kafka transactions for producing?

**A:** With an idempotent-but-non-transactional producer (which is what this repo's producer actually is —
`enable.idempotence=true`, but no `KafkaTransactionManager`/`transactional.id` in play), `read_committed` on
the *consumer* side mostly matters as a forward-looking safety setting: it tells the consumer to only ever
surface records that are part of a committed (not aborted) producer transaction, which is a no-op when nothing
is actually producing transactionally, but means nothing has to change on the consumer side if a transactional
producer were introduced later — the consumer is already configured to do the safe thing. Worth being precise
in an interview about what this setting does *not* do: it has no bearing on the application-level "did my own
database transaction commit" concerns this repo solves separately via the outbox and inbox patterns — it's
purely about Kafka's own internal transactional-producer protocol.

### Q: Walk through exactly what happens when a `@KafkaListener` method throws an exception.

**A:** The listener is deliberately written to **never** catch and acknowledge on failure — every listener in
this repo (`InventoryEventListener`, `OrderEventListener`, `NotificationEventListener`) just lets the exception
propagate. `KafkaEventConfig.kafkaErrorHandler()` (a `DefaultErrorHandler`, registered as the container
factory's `commonErrorHandler`) then takes over: it retries the **same record** with exponential backoff
(`ExponentialBackOffWithMaxRetries` — 1 second initial interval, ×2 multiplier, capped at 30 seconds, default 5
total attempts including the first), so a transient failure (a brief DB lock conflict, a momentary network
blip) has several chances to simply heal itself on retry without any message ever being lost or skipped.

Only once attempts are exhausted — or the handler recognizes the failure can **never** succeed no matter how
many times it retries — does the record get routed to the dead-letter topic via `DeadLetterPublishingRecoverer`
(see [05](05-dead-letter-queue-and-recovery.md)), and only then does the partition's offset actually advance
past that record (`handler.setCommitRecovered(true)` — the recovered/dead-lettered record's offset is committed
specifically so the partition isn't stuck retrying it forever once it's been safely parked elsewhere).

### Q: How does the error handler know the difference between "retry this, it might heal" and "never retry
this, it will always fail"?

**A:** `handler.addNotRetryableExceptions(NonRetryableEventException.class)` — a specific exception type this
codebase defines precisely for this purpose (seen used in, e.g., `ReservationService.normalize()` for a
structurally invalid event — a malformed line item is never going to become valid by trying again in 30
seconds). Throwing `NonRetryableEventException` skips the exponential backoff entirely and dead-letters
immediately on the first failure — the right behavior, since retrying a message that is fundamentally malformed
just delays an operator finding out about it for no benefit, burning through the retry budget on something
retrying can never fix. Anything else thrown is assumed potentially transient and gets the full retry
treatment.

### Q: What's `MAX_POLL_RECORDS_CONFIG = 100` actually controlling, and what's the tradeoff in either
direction?

**A:** The maximum number of records returned by a single `poll()` call to the consumer — effectively a batch
size for how much work one polling cycle hands to the listener container before committing progress and
polling again. Set it too high and a single slow batch risks exceeding the consumer group's session timeout
(`SESSION_TIMEOUT_MS_CONFIG`, 30 seconds here) before the batch finishes processing, which triggers an
unwanted consumer-group rebalance (the broker assumes a consumer that hasn't polled/heartbeated in time has
died, and reassigns its partitions) — actively disruptive, not just slow. Set it too low and you pay more
per-batch polling overhead for less throughput per round trip. 100 is a moderate, conservative default given
this repo's 30-second session timeout — comfortably processable within that window for the kind of per-record
database work (a stock reservation, a payment attempt) these listeners actually do.
