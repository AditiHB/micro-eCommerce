# Spring Kafka Configuration

### Q: What does a real `@KafkaListener` method look like in this repo, and what convention does every one
of them follow?

**A:**
```java
@KafkaListener(topics = Topics.ORDER_CREATED, groupId = Topics.GROUP_INVENTORY)
public void handleOrderCreated(OrderCreatedEvent event) {
    handler.onOrderCreated(event);
}
```
(`InventoryEventListener.java`) — and every listener class in this repo follows the same shape: **deliberately
thin**. The method itself does nothing but delegate straight to a handler class (`InventorySagaHandler`,
`OrderSagaHandler`, etc.); all real logic — including idempotency checks — lives there, not in the
`@KafkaListener`-annotated method. The class's own Javadoc states the convention explicitly:
*"Listeners never catch an exception and acknowledge: they let it propagate."* This split matters for
testability as much as clarity — the handler can be unit-tested with plain Mockito, with zero Kafka/Spring
context involved at all, while the thin listener method is really just wiring.

### Q: How does a listener method receive a typed `OrderCreatedEvent` object directly, rather than a raw
JSON string it has to parse itself?

**A:** The consumer factory's value deserializer chain does this automatically:
`ErrorHandlingDeserializer` wraps a `JsonDeserializer` (`consumerFactory()`), which uses
`JsonDeserializer.TYPE_MAPPINGS` — populated from `EventCatalog.typeMapping()` — to map the event's logical
type name (carried in a header, not the payload) to the actual Java class to deserialize into. The listener
method's own parameter type (`OrderCreatedEvent event`) then just receives the already-deserialized object;
Spring Kafka matches the incoming record's resolved type against the listener method's declared parameter type
automatically.

### Q: Why wrap `JsonDeserializer` in an `ErrorHandlingDeserializer` instead of using it directly?

**A:** Without the wrapper, a message that fails to deserialize (malformed JSON, or a type mapping that
doesn't match any registered class) throws **during deserialization itself** — before the listener container
even gets a chance to hand the record to the error-handling machinery described in
[03](03-consumers-offsets-and-error-handling.md), which only wraps *listener method* exceptions, not
deserialization ones. `ErrorHandlingDeserializer` catches that failure at the deserialization layer and
re-packages it so the container's normal `DefaultErrorHandler`/`DeadLetterPublishingRecoverer` pipeline can
still process it the same way as any other failure — a genuinely corrupt or unreadable message still ends up
parked in the dead-letter topic for inspection, rather than crashing the consumer thread outright.

### Q: `TRUSTED_PACKAGES` + `TYPE_MAPPINGS` together — what security problem is this actually solving, beyond
just "configuring deserialization"?

**A:** `JsonDeserializer.TRUSTED_PACKAGES = "com.ecommerce.common.events"` plus an explicit
`TYPE_MAPPINGS` from `EventCatalog` together form a **deserialization allow-list**: only the specific, named
classes in that mapping can ever be instantiated from an incoming Kafka message, and only from one trusted
package. The class's own Javadoc makes the underlying threat model explicit: *"an allow-list - no Java class
names on the wire, no `"*"` trusted packages."* Without this, a `JsonDeserializer` configured permissively (or
one relying on a class-name header a producer controls) is a well-known gadget-chain deserialization attack
surface — a malicious or compromised producer could potentially get a consumer to instantiate an arbitrary
class on the classpath just by naming it in a header. Keying deserialization off a small, explicit,
logical-name-to-class allow-list closes that off entirely: even a fully malicious message can only ever become
one of the specific event classes this system already expects, never anything else.

### Q: What does `factory.setConcurrency(concurrency)` actually create, mechanically?

**A:** `ConcurrentKafkaListenerContainerFactory` with concurrency `N` creates `N` separate
`KafkaMessageListenerContainer` instances (and `N` separate `Consumer` instances) for a given
`@KafkaListener`, each independently participating in the same consumer group and each getting assigned a
subset of the topic's partitions by the group's partition-assignment strategy. With `concurrency=3` matched to
`partitions=3` (deliberately equal, per the config's own comment), one service instance alone can already have
one consumer thread actively pulling from each of the topic's 3 partitions simultaneously — which is also
exactly why adding a 4th concurrent consumer (whether via higher concurrency or an additional service replica)
would sit **idle**: Kafka only ever assigns a partition to one consumer in a group at a time, so a 4th consumer
for a 3-partition topic simply has nothing to do.
