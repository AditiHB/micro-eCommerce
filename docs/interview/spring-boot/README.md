# Spring Boot Interview Prep

Interview-style question-and-answer guide to the Spring Boot / Spring Framework mechanics actually used in
this repository — dependency injection, auto-configuration, JPA, transactions, validation, AOP proxies,
caching, testing, actuator, and Jackson. This is the framework-level companion to
[`docs/interview/microservices/`](../microservices/README.md), which covers the architectural/distributed-systems
concepts (Config Server, Eureka, sagas, Kafka, resilience) built on top of this foundation — some topics are
cross-referenced between the two rather than duplicated.

Every answer references an exact file and line from this codebase, so you can open it and see the real pattern.

| # | Topic | What it covers |
|---|-------|----------------|
| [01](01-dependency-injection-and-bean-wiring.md) | Dependency injection & bean wiring | Constructor injection, `@Qualifier`, Lombok's role, why field injection is absent |
| [02](02-auto-configuration-and-conditional-beans.md) | Auto-configuration & conditional beans | How Boot decides what to wire, `@ConditionalOnProperty`, overriding an auto-configured bean |
| [03](03-configuration-properties-and-profiles.md) | Configuration properties & profiles | `@ConfigurationProperties` vs `@Value`, profile file conventions, relaxed binding |
| [04](04-spring-data-jpa.md) | Spring Data JPA | Derived queries, JPQL vs native `@Query`, `@Modifying`, pagination, locking |
| [05](05-transactions.md) | Transactions | `@Transactional` propagation, rollback rules, the gateway-inside-a-transaction pitfall |
| [06](06-validation-and-exception-handling.md) | Validation & exception handling | Bean Validation, `@Valid` cascading, the shared `ProblemDetail`/RFC 9457 exception hierarchy |
| [07](07-aop-proxies-and-cross-cutting-concerns.md) | AOP, proxies & cross-cutting concerns | JDK proxy vs CGLIB, the self-invocation limitation shared by `@Async`/`@Transactional`/`@Cacheable` |
| [08](08-caching-abstraction.md) | Caching abstraction | `@Cacheable`/`@CacheEvict` mechanics, SpEL keys, how it's proxy-based too |
| [09](09-testing-spring-boot-applications.md) | Testing Spring Boot applications | The test-slice spectrum, `@MockBean` vs plain Mockito, why this repo predates `@MockitoBean` |
| [10](10-actuator-and-health-checks.md) | Actuator & health checks | Custom `HealthIndicator`, what's exposed and why, liveness/readiness vs. a component health check |
| [11](11-jackson-and-serialization.md) | Jackson & serialization | The shared `ObjectMapper`, the `ProblemDetail` mixin, date handling |

## How to use this with the repo

As with the microservices guide: when an answer cites a file and line, open it. These aren't illustrative
examples invented for the doc — they're the actual code.
