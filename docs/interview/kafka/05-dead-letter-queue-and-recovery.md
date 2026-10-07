# Dead-Letter Queue & Recovery

### Q: What is `DeadLetterPublishingRecoverer`, and what does it actually put on the wire when a message is
dead-lettered?

**A:** It's the Spring Kafka component `KafkaEventConfig.kafkaErrorHandler()` hands a message to once retries
are exhausted (or a `NonRetryableEventException` short-circuits straight there — see
[03](03-consumers-offsets-and-error-handling.md)). It republishes the failed record to `<original-topic>-dlq`
(the destination resolver: `(record, ex) -> new TopicPartition(record.topic() + Topics.DLQ_SUFFIX, -1)` —
partition `-1` lets Kafka pick, since the DLQ topic only has 1 partition anyway), carrying the **original
topic, partition, offset, consumer group, and exception** along as headers, so nothing about where and why it
failed is lost just because it moved to a different topic.

### Q: Why does this repo configure *two separate* Kafka templates for dead-lettering
(`deadLetterBytesTemplate` and `deadLetterEventTemplate`), registered against different Java types?

**A:** `DeadLetterPublishingRecoverer` is constructed with a `Map<Class<?>, KafkaOperations<?, ?>>` — one
template per possible *shape* of failure. A message that failed because its JSON was genuinely malformed or
its type mapping didn't resolve never became a typed object at all; the recoverer still needs to republish
*something* representing it, so the `byte[].class` template (`ByteArraySerializer`) exists specifically to
dead-letter the **raw, undeserializable bytes** when there's no typed object to work with. The
`Object.class`/`EventJsonSerializer` template handles the normal case — a message that deserialized fine but
whose *listener* then failed. Having both means a dead-lettered message is always inspectable later regardless
of which layer actually failed, rather than losing the raw payload entirely in the "couldn't even parse it"
case.

### Q: Why does the DLQ consumer stack (`dlqConsumerFactory`) deliberately use plain `StringDeserializer` for
both key and value, instead of reusing the main `JsonDeserializer`/type-mapping setup?

**A:** The class's own comment states the reasoning directly: *"DLQ topics carry plain text; this stack is
deliberately separate ... so a message that does not deserialize cleanly can still be read, parked and
inspected."* The entire *point* of a dead-letter queue is to catch messages that might be malformed or
unreadable — reusing the strict, type-mapped `JsonDeserializer` on the consuming side of the DLQ would mean a
message dead-lettered *because* it was malformed could then fail to deserialize **again** on the DLQ consumer
side, defeating the purpose. A plain string deserializer can never itself fail this way — whatever bytes are on
the wire, you get a string back, readable and parkable regardless of its actual shape.

### Q: Walk through `DeadLetterQueueHandler`'s own consumer — what does it listen to, and what stops one
service from parking another service's dead letters?

**A:** It subscribes to a **topic pattern**, not a fixed list: `topicPattern = "${kafka.dlq.pattern:.*-dlq$}"` —
every dead-letter topic in the system, whichever service's original topic it came from, with its own consumer
group `${spring.application.name}-dlq` (so each service runs its own independent DLQ-watching consumer, not one
shared process for the whole system). Inside the handler, it filters to only the records whose
`DLT_ORIGINAL_CONSUMER_GROUP` header matches **this service's own** consumer groups — another service's failure
that happens to land in a DLQ topic this consumer also happens to be subscribed to (since the pattern matches
broadly) gets skipped, not parked, so every service owns exactly its own dead letters and nothing else's.

### Q: Once a message is parked as a `DeadLetter` with status `PARKED`, what actually happens to it — is it
lost, or can an operator do something about it?

**A:** Not lost — it's durably recorded (a `DeadLetter` row, deduplicated by
`(originalTopic, originalPartition, originalOffset, consumerGroup)` so redelivery of the same dead letter from
Kafka's own retry/rebalance behavior doesn't create duplicate park records), and exposed via an admin-only REST
API (`DeadLetterController`, `GET`/`POST` on `/api/v1/dead-letters`, gated to `ROLE_ADMIN`) backed by
`DeadLetterService`. A counter (`ecommerce.dlq.received`) and a gauge (`ecommerce.dlq.parked`) feed monitoring,
so a spike in dead letters is visible on a dashboard, not just discoverable by someone remembering to query the
table. The intent is clearly operator-driven recovery — inspect what failed and why (the original
topic/partition/offset/exception are all preserved), decide whether it's safe to replay, and act — rather than
either silently dropping failed messages or endlessly retrying something that will never succeed.
