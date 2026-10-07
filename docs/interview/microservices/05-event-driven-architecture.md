# Event-Driven Architecture

### Q: Why use events between services instead of just more REST calls?

**A:** REST calls couple the caller to the callee being available *right now* — if `payment-service` is down,
a synchronous "charge the customer" call fails the whole order. Events decouple that: `order-service` publishes
"order created" and moves on; whoever cares (payment, inventory, notification) consumes it whenever they're
ready, including after being down for a while. The tradeoff is consistency timing: the system is only
*eventually* consistent (an order can briefly exist before payment has been attempted), which is why the saga
pattern exists to manage that explicitly (see [06](06-saga-and-distributed-transactions.md)).

### Q: How are Kafka topics, partitions, and consumer groups configured here, and why does partition count
matter?

**A:** Every business topic (`order-events`, `payment-events`, `inventory-events`, `product-events`) gets **3
partitions** by default (`kafka.event.partitions:3` in `KafkaEventConfig.java`), with matching consumer
concurrency of 3 listener threads per consumer-group member. Dead-letter topics get 1 partition each. Topic
auto-creation is explicitly disabled on the broker (`KAFKA_AUTO_CREATE_TOPICS_ENABLE: "false"`) — topics only
come into existence via explicit `NewTopic` beans, so partition count is a deliberate decision, not whatever
the first producer happened to trigger.

Why it matters: **a topic's partition count is the hard ceiling on how many consumer instances can process it
in parallel**, full stop — it doesn't matter how many replicas of a service you run, only 3 of them (combined,
across all replicas) can ever be actively consuming a 3-partition topic at once, because Kafka assigns each
partition to exactly one consumer within a group. Scaling `inventory-service` from 2 to 10 pods buys you
nothing for processing `order-events` faster if that topic only has 3 partitions.

### Q: What's the outbox pattern, and what problem does it solve that a Kafka send alone doesn't?

**A:** The problem: if a service does "write to its own database" and "publish an event" as two separate
operations, there's a window where one succeeds and the other fails — the DB commits but the Kafka send never
happens (or vice versa), leaving other services with a view of the world that disagrees with this service's own
database.

The outbox pattern fixes this by writing the event to an `outbox_event` **table in the same local database
transaction** as the business change — so they're atomically all-or-nothing by ordinary ACID guarantees, no
distributed transaction needed. A separate background poller (`OutboxRelay`, `common/.../outbox/`) then reads
due rows and actually hands them to Kafka, marking them published only after the broker acknowledges
(`acks=all`). If the poller crashes mid-send, the row is simply still `PENDING` and gets picked up again next
poll — this is what makes delivery **at-least-once**, never zero-times.

`OutboxRelay` locks its batch with `SELECT ... FOR UPDATE SKIP LOCKED`, so any number of replicas can run the
poller concurrently without double-sending the same row. It also dispatches a whole batch's Kafka sends
concurrently (collecting the `CompletableFuture<SendResult<...>>`s before blocking on any of them) rather than
one at a time — a `CompletableFuture` fan-out-then-join pattern that predates, and matches, the ones added
elsewhere this session.

### Q: At-least-once delivery means a consumer might see the same event twice. How does this repo stop that
from double-applying a side effect?

**A:** Idempotent consumers via an **inbox** pattern: a `processed_events` table records which event IDs a
given consumer has already handled. Before acting on a redelivered event, the consumer checks whether it's
already processed that ID (and, in `ReservationService.reserve()`, additionally checks
`reservationRepository.existsByOrderId(orderId)` directly) — a replay of an already-handled event becomes a
no-op logged at `info`, not a double-charge or double-reservation.

### Q: What happens when a Kafka consumer keeps failing to process a message — does it retry forever?

**A:** No — after retries are exhausted (configured per-service's listener container factory), Spring Kafka's
built-in dead-letter-topic mechanism routes the record to a `<topic>-dlq` topic. `DeadLetterQueueHandler`
(`common/.../events/`) consumes `.*-dlq$` topics, filtered to only the records that belong to **its own
service's** consumer group (read from Kafka's `DLT_ORIGINAL_CONSUMER_GROUP` header — another service's dead
letter isn't this service's problem), deduplicates by `(originalTopic, partition, offset, consumerGroup)`, and
persists it as a `DeadLetter` row with status `PARKED`. A counter/gauge (`ecommerce.dlq.received`/
`ecommerce.dlq.parked`) feeds an alert, and an admin-only REST API (`DeadLetterController`,
`/api/v1/dead-letters`) lets an operator inspect and (per its service layer) replay parked messages rather than
losing them silently.

### Q: How do you keep producers and consumers from breaking each other when an event's shape changes?

**A:** Contract tests using **golden JSON files**, not a full Pact-style broker. Each event type has a sample
serialized and compared byte-for-byte (after normalization) against a checked-in
`contracts/<EventType>.v<version>.json` file (`ProductEventContractTest.java` and siblings per service). If a
field is accidentally renamed or removed, the test fails immediately against the frozen golden file instead of
only being discovered when a consumer silently stops deserializing a field correctly in production. The golden
files are intentionally regenerated only explicitly (`-Dcontracts.update=true`), so a schema change is always a
deliberate, reviewed diff — never an accidental side effect of running the test suite.

### Q: Is there event sourcing in this repo — i.e. can you reconstruct an aggregate's current state purely by
replaying its events?

**A:** No, and it's worth being precise about this distinction in an interview. `EventStoreRepository`/
`EventSourcingService` (`common/.../eventsourcing/`) persist every domain event that goes through the outbox
(`EventPublisher.publish()` calls `storeEvent()` unconditionally, in the same transaction) — but there is no
`apply()`/fold logic anywhere that reconstructs current state from that history. It's an **audit log**, queryable
by aggregate ID, type, correlation ID, or time range — valuable for debugging and compliance ("show me
everything that happened to order 42"), but the actual current state of an order still lives in `order-service`'s
own `orders`/`order_lines` tables, not derived by replaying the event store. True event sourcing would make the
event log the single source of truth and the tables a projection of it; here it's the reverse.
