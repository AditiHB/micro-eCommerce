# Producers & Delivery Guarantees

### Q: What does `acks=all` actually mean, and why is it the one producer setting this repo is unwilling to
relax?

**A:** `acks` controls how many replicas must confirm a write before the producer considers it successful.
`acks=0` doesn't wait at all (fire and forget — fastest, but a broker crash right after the write can lose it
silently); `acks=1` waits for the partition leader only (lost if the leader dies before followers replicate
it); `acks=all` (what `KafkaEventConfig.baseProducerProps()` sets unconditionally) waits for every in-sync
replica to confirm. This repo's whole delivery guarantee — the outbox pattern's "a row is marked published only
once Kafka has truly accepted it" promise (see
[../microservices/05](../microservices/05-event-driven-architecture.md)) — depends entirely on `acks=all`
actually meaning the message is durable once acknowledged; anything weaker would reopen the exact
"acknowledged but then lost" gap the outbox pattern exists to close.

### Q: What does `ENABLE_IDEMPOTENCE_CONFIG = true` add on top of `acks=all`?

**A:** `acks=all` only guarantees a write that succeeds is durable — it says nothing about what happens if the
producer times out waiting for that acknowledgment and **retries** a write that actually already landed
(a classic ambiguous-timeout scenario: did the ack get lost, or did the write never happen?). Without
idempotent production, a retried send after an ambiguous timeout could create a genuine duplicate message on
the broker itself, at the Kafka level — a different failure mode entirely from the *application-level*
duplicate-processing idempotency the inbox/outbox pattern already guards against (see
[../microservices/05](../microservices/05-event-driven-architecture.md)/[06](../microservices/06-saga-and-distributed-transactions.md)).
`ENABLE_IDEMPOTENCE_CONFIG = true` has the broker deduplicate retried sends from the *same* producer session by
sequence number, so a producer-side retry after a timeout can never create a duplicate record on the broker,
closing a gap `acks=all` alone leaves open.

### Q: Why does `KafkaEventConfig` bother setting `MAX_BLOCK_MS_CONFIG`, `REQUEST_TIMEOUT_MS_CONFIG`, and
`DELIVERY_TIMEOUT_MS_CONFIG` explicitly, rather than trusting Kafka client defaults?

**A:** The class's own comment states the reason directly: *"The relay's sends block the polling thread only
for these bounds, so a broker outage shows up as a quick, retried failure rather than a hung scheduler."*
`OutboxRelay` runs on a `@Scheduled` polling thread — if a send to an unreachable broker were allowed to block
indefinitely (or for whatever the client library's own generous defaults happen to be), that scheduled poll
would simply never return, and the *next* scheduled poll would never fire either, silently stalling the entire
outbox-to-Kafka pipeline with no obvious symptom beyond "events stopped flowing." Bounding
`max.block.ms`/`request.timeout.ms`/`delivery.timeout.ms` explicitly (3s/5s/15s by default) guarantees a broker
outage surfaces as a bounded, logged, retried failure on the next poll instead of an indefinitely wedged
scheduler thread.

### Q: Business services never call `KafkaTemplate.send(...)` directly — why not, and what calls it instead?

**A:** `KafkaEventConfig`'s own Javadoc states this as a deliberate rule: *"Business code does not produce to
Kafka at all - it writes the outbox, and the relay uses `outboxKafkaTemplate()`."* Every event this system ever
publishes goes: business transaction writes a row to the local `outbox_event` table (same database transaction
as the business change) → `OutboxRelay`'s scheduled poll reads due rows and is the *only* code path that ever
calls `kafka.send(...)`. This is what makes the outbox pattern's atomicity guarantee actually hold in practice —
if business code could *also* reach for `KafkaTemplate` directly for a "quick" send somewhere, that send would
have no transactional relationship to the business write at all, silently reopening the exact dual-write
problem (DB commits, Kafka send fails or vice versa) the whole pattern exists to close. One code path, one
producer, no exceptions.

### Q: What serialization does the outbox's producer actually use, given events are JSON objects?

**A:** `String`-to-`String` (`outboxProducerFactory()`: `KEY_SERIALIZER_CLASS_CONFIG` and
`VALUE_SERIALIZER_CLASS_CONFIG` both `StringSerializer`) — the event is already serialized to a JSON string
*before* it ever reaches the outbox table (`OutboxEvent.payload`), by the same shared `ObjectMapper` discussed
in the [Spring Boot guide](../spring-boot/11-jackson-and-serialization.md). Kafka itself never sees a Java
object on the producer side at all; it's handling plain strings, with the event's logical type name carried
separately as a header (`__TypeId__`, `eventType`) so a consumer knows what to deserialize the string *into*
without that information needing to be encoded in the payload itself. This is distinct from the dead-letter
path, which uses a different producer stack entirely — see [05](05-dead-letter-queue-and-recovery.md).
