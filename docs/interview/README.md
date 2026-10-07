# Interview Prep

Interview-style question-and-answer guides, grounded in this actual repository rather than generic theory.
Every answer references an exact file and line — open it and the pattern discussed is really there.

- **[microservices/](microservices/README.md)** — architecture and distributed-systems concepts: service
  boundaries, service discovery, configuration centralization, resilience patterns, event-driven architecture,
  sagas, data consistency, concurrency, caching, security, observability, deployment and scaling.
- **[spring-boot/](spring-boot/README.md)** — the Spring Boot/Spring Framework mechanics underneath: dependency
  injection, auto-configuration, JPA, transactions, validation and exception handling, AOP proxies, caching
  internals, testing, actuator, and Jackson.
- **[kafka/](kafka/README.md)** — a deep dive into this repo's one Kafka configuration class: topics,
  partitions and ordering, producer delivery guarantees, consumer offset/error handling, Spring Kafka wiring,
  dead-letter recovery, schema evolution, and KRaft cluster topology.
- **[docker/](docker/README.md)** — how the services are actually built into images: multi-stage Dockerfiles,
  layer-cache-friendly `COPY` ordering, Compose health-gated startup ordering and profiles.
- **[kubernetes/](kubernetes/README.md)** — how those images are actually deployed and scaled: Deployments vs.
  StatefulSets, the HPA's real ceiling, liveness/readiness-driven self-healing, Secrets/RBAC, default-deny
  networking, and Kustomize's base-plus-overlays structure.

Read the microservices guide for "how does this system work as a whole"; the Spring Boot guide for "how does
the framework underneath actually make that happen"; the Kafka guide when a question goes deeper into the
messaging layer than the microservices guide's event-driven-architecture chapter does; and the Docker/
Kubernetes guides for "how does this actually get built and run," moving from a single image to a whole
cluster. All five cross-reference each other where a topic genuinely spans more than one — concurrency and
caching show up from an architectural angle and a framework-mechanics angle; events show up architecturally and
at the Kafka-specific mechanical level; security and scaling show up across the microservices, Docker, and
Kubernetes guides at their own respective layers.
