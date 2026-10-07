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

Read the microservices guide for "how does this system work as a whole"; read the Spring Boot guide for "how
does the framework underneath actually make that happen." The two cross-reference each other where a topic
genuinely spans both (concurrency and caching, in particular, show up from both an architectural angle and a
framework-mechanics angle).
