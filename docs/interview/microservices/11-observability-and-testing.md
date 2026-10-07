# Observability & Testing

### Q: How do you trace a single request as it moves across multiple services in this repo?

**A:** A custom `X-Trace-ID` header, generated or propagated by `RequestResponseLoggingFilter` (`common/.../logging/`)
and echoed back on the response — every log line for that request can be grepped by this ID across services'
logs. Worth being precise here in an interview: this is **not** a full distributed-tracing system — there's no
Micrometer Tracing/Sleuth/Zipkin/Brave dependency anywhere in this repo, no span tree, no visual waterfall of
where time was spent across services. It's log correlation, which answers "show me everything that happened
for this request" but not "which specific hop took 400ms." A production system expecting to debug cross-service
*latency*, not just correlate logs, would want to add real distributed tracing on top of this.

Separately, at the business level, events carry their own `correlationId`/`causationId`
(`EventPublisher.publish()`) — tracking "which event caused which other event" through the saga, independent of
and for a different purpose than the request-level trace ID above.

### Q: What's the difference between liveness and readiness probes, and how does this repo use both?

**A:** **Liveness** answers "is this process healthy enough to keep running, or should Kubernetes kill and
restart it" — a failing liveness probe gets the pod restarted. **Readiness** answers "is this instance ready to
receive traffic right now" — a failing readiness probe just pulls the pod out of the Service's load-balancing
rotation, without restarting it; traffic routes to other healthy replicas instead until it recovers on its own
(e.g. still warming up, or a downstream dependency it needs is briefly unreachable).

`order-service`'s Deployment manifest sets both against Spring Boot Actuator's own probe-aware endpoints:
`/actuator/health/liveness` (60s initial delay, every 15s, 3 failures before restart) and
`/actuator/health/readiness` (45s initial delay, every 5s — checked more frequently, since "temporarily not
ready" is meant to be a quick, recoverable state, not a crash-and-restart situation).

### Q: What's actually exposed via Spring Boot Actuator here, and why not expose everything?

**A:** `health`, `prometheus`, `info`, and `busrefresh` (`config-repo/application.yml`) —
`management.endpoint.health.show-details: never` additionally keeps health-check *details* (which specific
component failed, connection strings, etc.) out of the response body entirely, exposing only up/down. Actuator
has endpoints that can dump environment variables, thread dumps, or trigger a heap dump — exposing those
publicly (or even just inside the mesh without access control) is a real information-disclosure and DoS risk,
so the exposure list is kept to exactly what health checks, metrics scraping, and the config-refresh mechanism
actually need.

### Q: What's the monitoring stack, and what does it actually measure?

**A:** Prometheus scrapes each service's `/actuator/prometheus` endpoint; Grafana visualizes it via provisioned
dashboards and datasources (`docker-compose.yml`). `ApplicationMetrics` (`common/.../metrics/`) defines the
application-level technical KPIs fed into this: counters (customers/orders/payments created, inventory
reservations) and timers (creation/processing durations) — the kind of metric that answers "how many" and "how
long," distinct from the log-based correlation discussed above, which answers "what happened, in order."

### Q: Why does this repo use real Postgres and Redis containers (Testcontainers) for integration tests
instead of H2 or mocks?

**A:** H2 and mocks can both pass a test while hiding a real production bug. H2 doesn't enforce the same SQL
semantics, constraint behavior, or locking semantics as Postgres — a pessimistic `SELECT ... FOR UPDATE` or a
partial index predicate can behave differently, or not exist at all, in H2. Mocking the database entirely means
Hibernate's actual generated SQL, and whether your entity mappings actually match your Flyway-migrated schema,
is never verified at all.

`SharedPostgres`/`SharedRedis` (`common/.../testsupport/`) start one real container *statically*, once per JVM,
shared across every integration test class — not a fresh container per test (too slow) and not a shared
long-running service (too stateful between unrelated test runs). Tests run real Flyway migrations and
Hibernate `ddl-auto=validate` against it, which is what actually catches "my `@Entity` doesn't match the
migration I wrote" before it reaches a real environment. `SharedRedis` additionally exposes `pause()`/`resume()`
to genuinely simulate a Redis outage (a real Docker pause, not a mocked exception) for testing the
cache-failure-isolation behavior discussed in [09](09-caching-strategies.md).

### Q: Beyond unit and integration tests, what does the Karate e2e suite add?

**A:** It runs `.feature` files as real HTTP requests against the **live, fully assembled stack** — real
Keycloak tokens via an actual password grant, not a mocked `Authentication` object — covering things no
single-service test can: `authorization.feature` (a real customer token genuinely getting `403` on back-office
endpoints), `compensating-transaction.feature` (a saga's rollback actually happening end-to-end across real
service boundaries), `dlq-routing.feature`, `rate-limit-probe.feature`, `resilience.feature` (the circuit
breaker actually tripping under a real simulated outage). This is the layer that verifies the *system*
behaves correctly, not just that each service's own code is individually correct in isolation — the two
failure modes genuinely differ: a service can pass every one of its own tests and still integrate incorrectly
with the real deployed topology.

### Q: How does this repo verify that a producer and consumer agree on an event's shape, given they're
different services that might deploy independently?

**A:** See [05](05-event-driven-architecture.md)'s contract-testing answer — golden-file schema tests, checked
in and compared on every build, so a breaking change to an event's shape is caught at build time in whichever
service made the change, rather than discovered only when some other, independently-deployed consumer breaks in
production.
