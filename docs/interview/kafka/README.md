# Kafka Interview Prep

Interview-style question-and-answer guide to the Kafka concepts and configuration actually used in this
repository — one `@Configuration` class, `common/src/main/java/com/ecommerce/common/config/KafkaEventConfig.java`,
drives almost everything here, and is worth reading end to end alongside this guide. This is the Kafka-specific
deep dive underneath [`../microservices/05-event-driven-architecture.md`](../microservices/05-event-driven-architecture.md)
(which covers *why* event-driven architecture and the outbox pattern exist) and
[`../microservices/06-saga-and-distributed-transactions.md`](../microservices/06-saga-and-distributed-transactions.md)
(which covers how the saga is choreographed over these same topics).

| # | Topic | What it covers |
|---|-------|----------------|
| [01](01-topics-partitions-and-ordering.md) | Topics, partitions & ordering | Per-event-type topic naming, partition count as a hard parallelism ceiling, message-key-driven ordering |
| [02](02-producers-and-delivery-guarantees.md) | Producers & delivery guarantees | `acks=all`, idempotent producers, bounded timeouts, why business code never produces directly |
| [03](03-consumers-offsets-and-error-handling.md) | Consumers, offsets & error handling | Manual per-record ack, `read_committed`, retry-then-dead-letter via `DefaultErrorHandler` |
| [04](04-spring-kafka-configuration.md) | Spring Kafka configuration | `@KafkaListener`, `ConcurrentKafkaListenerContainerFactory`, the type-mapping deserialization allow-list |
| [05](05-dead-letter-queue-and-recovery.md) | Dead-letter queue & recovery | `DeadLetterPublishingRecoverer`, the separate DLQ consumer stack, `DeadLetterQueueHandler`'s parking/replay flow |
| [06](06-schema-evolution-and-contract-testing.md) | Schema evolution & contract testing | Golden-file contract tests, why there's no schema registry here, versioning an event type |
| [07](07-kraft-and-cluster-topology.md) | KRaft & cluster topology | ZooKeeper-less Kafka, the single-node-vs-3-broker overlay, replication factor and `min.insync.replicas` |

## How to use this with the repo

Same discipline as the other two guides: every answer cites an exact file and line. Open
`KafkaEventConfig.java` once at the start — nearly every topic in this guide traces back to one specific
`@Bean` method or `@Value`-injected property in that single file.
