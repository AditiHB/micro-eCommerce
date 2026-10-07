# Microservices Interview Prep

Interview-style question-and-answer guide to the microservices concepts actually implemented in this
repository. Unlike [`docs/concepts/`](../../concepts/README.md) (tutorial-style, beginner-friendly explainers),
this folder is structured as Q&A you could be asked in an interview, with every answer grounded in a real file
and line number from this codebase — so you can open the file and see the pattern for yourself, not just take
the answer's word for it.

See also [`../spring-boot/`](../spring-boot/README.md) for the Spring Boot/Spring Framework mechanics
underneath these architectural patterns (dependency injection, auto-configuration, transactions, AOP proxies,
and more); [`../kafka/`](../kafka/README.md) for a deeper dive into the messaging layer than chapter 05 below
goes into (producer/consumer config, dead-letter recovery mechanics, KRaft cluster topology); and
[`../docker/`](../docker/README.md)/[`../kubernetes/`](../kubernetes/README.md) for a deeper dive into the
deployment layer than chapter 12 below goes into (Dockerfile mechanics, Compose orchestration, HPA internals,
Kustomize overlays).

Each file covers one topic area. Read them in order if you're studying end-to-end, or jump straight to the
topic you need.

| # | Topic | What it covers |
|---|-------|----------------|
| [01](01-architecture-and-service-boundaries.md) | Architecture & service boundaries | Service decomposition, database-per-service, API gateway, why `product-service` was merged into `inventory-service` |
| [02](02-service-discovery-and-configuration.md) | Service discovery & configuration | Eureka, client-side load balancing, Spring Cloud Config Server, config resolution order, Spring Cloud Bus |
| [03](03-inter-service-communication.md) | Inter-service communication | Sync REST calls, bearer-token relay, mTLS vs JWT, `CompletableFuture` fan-out |
| [04](04-resilience-patterns.md) | Resilience patterns | Circuit breakers, retries, timeouts, rate limiting, bulkheads (and the gap), graceful degradation |
| [05](05-event-driven-architecture.md) | Event-driven architecture | Kafka topics/partitions/consumer groups, outbox pattern, dead-letter queues, contract tests |
| [06](06-saga-and-distributed-transactions.md) | Saga & distributed transactions | Choreography vs orchestration, compensation, the saga-deadline reaper, idempotent consumers |
| [07](07-data-consistency-and-concurrency.md) | Data consistency & concurrency | Idempotency keys, optimistic vs pessimistic locking, preventing overselling |
| [08](08-async-processing-and-thread-safety.md) | Async processing & thread safety | `CompletableFuture` vs `@Async`, `SecurityContext` propagation, Hibernate session thread-safety |
| [09](09-caching-strategies.md) | Caching strategies | Cache-aside, transaction-aware eviction, cache-outage isolation, when *not* to cache |
| [10](10-security.md) | Security | OAuth2/JWT resource servers, JWKS validation, role vs ownership authorization, mTLS, secrets tiers |
| [11](11-observability-and-testing.md) | Observability & testing | Correlation IDs, actuator probes, Prometheus/Grafana, Testcontainers, contract tests, Karate e2e |
| [12](12-deployment-and-scaling.md) | Deployment & scaling | Docker Compose vs Kubernetes, HPA, liveness/readiness probes, pod hardening, network policies |

## How to use this with the repo

Every answer references real files. When an answer says something like *"see
`services/inventory-service/.../ReservationService.java:63`"*, that's not illustrative — open that exact line
and you'll see the pattern discussed. If you're prepping for an interview, try covering the answer and
explaining the concept yourself first, then check it against both the written answer and the actual code.
