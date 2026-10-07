# Interview Prep

Interview-style question-and-answer guides, grounded in this actual repository rather than generic theory.
Every answer in both guides references an exact file and line — open it and the pattern discussed is really
there.

- **[microservices/](microservices/README.md)** — architecture and distributed-systems concepts: service
  boundaries, service discovery, configuration centralization, resilience patterns, event-driven architecture,
  sagas, data consistency, concurrency, caching, security, observability, deployment and scaling.
- **[spring-boot/](spring-boot/README.md)** — the Spring Boot/Spring Framework mechanics underneath: dependency
  injection, auto-configuration, JPA, transactions, validation and exception handling, AOP proxies, caching
  internals, testing, actuator, and Jackson.
- **[kafka/](kafka/README.md)** — a deep dive into this repo's one Kafka configuration class: topics,
  partitions and ordering, producer delivery guarantees, consumer offset/error handling, Spring Kafka wiring,
  dead-letter recovery, schema evolution, and KRaft cluster topology.

Read the microservices guide for "how does this system work as a whole"; read the Spring Boot guide for "how
does the framework underneath actually make that happen"; read the Kafka guide when a question goes deeper into
the messaging layer specifically than the microservices guide's own event-driven-architecture chapter does. The
three cross-reference each other where a topic genuinely spans more than one (concurrency and caching, in
particular, show up from an architectural angle, a framework-mechanics angle, and — for events — a Kafka-specific
angle).
