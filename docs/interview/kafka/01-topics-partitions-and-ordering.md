# Topics, Partitions & Ordering

### Q: How are topics named in this repo — one generic topic per service/domain, or something finer-grained?

**A:** Finer-grained: one topic **per event type**, not per domain. `Topics.java` declares `order-created`,
`order-cancelled`, `inventory-reserved`, `inventory-failed`, `inventory-released`, `payment-processed`,
`payment-failed`, `refund-completed`, and one domain-level exception, `product-events`. This is a deliberate
naming choice worth being able to justify: a consumer that only cares about `payment-failed` can subscribe to
exactly that topic, instead of subscribing to a broad `payment-events` topic and filtering out
`payment-processed` messages it doesn't want — narrower topics mean a consumer's subscription *is* its filter,
with no wasted deserialization or discarding of messages it was never going to act on.

### Q: Where does partition count actually get decided, and what's the default?

**A:** `KafkaEventConfig.eventTopics()` generates a `NewTopic` for every name in `EventCatalog.topics()`
(derived from `Topics.java`), each with `partitions = kafka.event.partitions` (default **3**) and
`replicationFactor` (default 1, overridden to 3 by the HA overlay — see
[07](07-kraft-and-cluster-topology.md)). Every dead-letter topic (`<topic>-dlq`) gets its own, separately
configured `dlq-partitions` (default **1**). Broker auto-creation is explicitly disabled
(`KAFKA_AUTO_CREATE_TOPICS_ENABLE: "false"` in Compose) — a topic only exists because this `NewTopic` bean
declared it, never because some producer happened to be the first to reference an undeclared topic name.

### Q: Why does partition count matter more than almost any other single Kafka setting for this system's
scalability?

**A:** Kafka guarantees that a given partition is consumed by **exactly one** consumer instance within a
consumer group at a time — so partition count is a hard ceiling on parallel consumption, independent of how
many service replicas exist. With `kafka.event.partitions:3` and consumer concurrency also set to 3
(`kafka.consumer.concurrency:3`, matched deliberately), a single service instance can already consume an entire
3-partition topic's traffic on its own — running 5 replicas of that service buys you **zero** additional
throughput for that specific topic, because only 3 partitions exist to hand out regardless of how many
consumer threads are waiting across however many pods. This is the single most important capacity fact to know
about this system's event pipeline (see the capacity discussion referenced from
[../microservices/05](../microservices/05-event-driven-architecture.md)) — scaling pods and scaling partitions
are two independent levers, and only one of them actually raises this particular ceiling.

### Q: How does this repo guarantee that events for the *same* order are processed in the order they
happened, given a topic has multiple partitions?

**A:** Kafka only guarantees ordering **within a single partition**, never across partitions on the same
topic. The way to get effective per-entity ordering on a multi-partition topic is to make sure every message
for that entity always lands on the *same* partition — which Kafka does automatically whenever messages share
the same **key** (the default partitioner hashes the key to pick a partition deterministically). This repo sets
the Kafka message key to the event's **aggregate ID** —
`EventPublisher.java`: `.messageKey(event.getAggregateId())` — so every event about order 42 (created,
cancelled, whatever comes later) always lands on the same partition and is therefore always delivered to any
given consumer in the order it was produced, even though *other* orders' events may interleave with them
across the topic's other two partitions. Get the key choice wrong (say, keying by something that doesn't
correlate with the aggregate, or leaving it null so Kafka round-robins) and this per-order ordering guarantee
silently disappears — this is exactly the kind of detail that looks like a minor implementation choice but is
actually load-bearing for correctness.

### Q: If message ordering is only guaranteed per partition, and per-aggregate ordering depends entirely on
key choice, what would happen if two different event types for the same order ended up on *different* topics
with *different* keys?

**A:** You could lose relative ordering between them even though each individual topic's per-key ordering is
fine — e.g. if `order-created` used the order ID as its key but some other topic about the same order used a
different key (or none), there's no guarantee a consumer observing both topics sees them in the "real" causal
order, since Kafka makes no ordering promise *across* topics at all, ever. This repo avoids the problem by
keying consistently off `aggregateId` everywhere events are published (one shared code path —
`EventPublisher.publish()` — rather than each service inventing its own key choice), which is worth naming as
the actual mechanism that prevents this failure mode, not just "Kafka handles ordering."
