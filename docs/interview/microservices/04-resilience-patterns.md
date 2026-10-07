# Resilience Patterns

### Q: Walk through resilience4j's circuit breaker states and how this repo configures them.

**A:** Three states: **closed** (calls flow through normally, failures counted), **open** (calls fail
immediately without even trying the downstream — protects a struggling dependency from more load), and
**half-open** (after a wait, a few calls are let through as a probe; if they succeed, close again, if they
fail, re-open).

This repo's shared config (`config-repo/api-gateway.yml`, applied to all six gateway circuit breakers, and
mirrored for order-service's outbound clients in `config-repo/order-service.yml`): `slidingWindowSize: 10`,
`minimumNumberOfCalls: 5` (don't trip on too small a sample), `failureRateThreshold: 50`, `waitDurationInOpenState`
(10s for service-to-service calls, 30s at the gateway edge). The gateway additionally sets
`slowCallRateThreshold: 100` with `slowCallDurationThreshold: 2000ms` — calls that succeed but take too long
count toward tripping the breaker too, not just outright failures.

### Q: Why pair retry with a circuit breaker instead of just one or the other?

**A:** They solve different failure durations. A retry assumes the failure is **transient** — a dropped
packet, a brief GC pause — and a second attempt moments later will probably succeed; `order-service`'s retry on
`CatalogClient`/`CustomerDirectoryClient` is `maxAttempts: 2`, `waitDuration: 200ms`. A circuit breaker assumes
the failure might be **sustained** — the dependency is actually down — and that retrying into a dead service
repeatedly just adds load to something already struggling, so after enough failures it stops trying at all for
a cooldown window.

Critically, retries here are scoped to specific exception types (`ResourceAccessException`,
`HttpServerErrorException`) — **reads only**. There is deliberately no retry around `POST /orders`: a retried
order creation could create a duplicate order if the first attempt actually succeeded but the response was
lost. (The actual safety net for that scenario is the idempotency key, not a retry — see
[07](07-data-consistency-and-concurrency.md).)

### Q: What rate limiting does this repo have, and why a custom implementation instead of Spring Cloud
Gateway's built-in `RequestRateLimiter`?

**A:** A custom Redis-backed fixed-window limiter (`RateLimitingFilter.java` in `api-gateway`): per-client
(keyed by `X-Forwarded-For`, falling back to remote IP), `INCR` + `EXPIRE 60s` in Redis, returning `429` over
the limit. Limits are per-route in `config-repo/api-gateway.yml` (order-service 100/min, inventory-service
200/min, payment-service 50/min — payment intentionally tighter). Worth noticing as an interview point: this
makes the gateway's Redis connection pool itself a potential serialization point at very high request rates —
every request's rate-limit check goes through it.

### Q: What's a bulkhead, and does this repo have one?

**A:** A bulkhead isolates one dependency's failures from consuming shared capacity needed by everything
else — named for ship compartments that keep one hull breach from sinking the whole vessel. Resilience4j offers
this as a thread-pool or semaphore bulkhead per call type.

Honest answer for this repo: **no Resilience4j bulkhead is configured anywhere** (checked — zero matches for
`bulkhead` across `config-repo/`). The closest thing that exists is the hand-rolled, purpose-sized executor
pools added for `CompletableFuture`/`@Async` work (`remoteCallExecutor`, `sagaReaperExecutor`,
`notificationSendExecutor`, `cacheEvictionExecutor`, `customerMetricsExecutor` — see
[08](08-async-processing-and-thread-safety.md)), each bounded and separate from the others precisely so one
doesn't starve another. But within `order-service`, a single slow `customer-service` and a single slow
`inventory-service` both compete for the *same* `remoteCallExecutor` pool today — a genuine bulkhead would give
each dependency its own isolated pool/semaphore so a degraded customer-service specifically couldn't also
starve catalog lookups. This is a real, identifiable gap, good to be able to name in an interview rather than
claim the repo has something it doesn't.

### Q: What happens, end to end, if `inventory-service` is completely down when a client tries to place an
order?

**A:** `CatalogClient.lookup()` fails (connection refused/timeout) → caught by `OrderPlacementService.remote()`
→ converted into a `DependencyUnavailableException` → surfaced to the client as a clean `503 Service
Unavailable`, not a stack trace or a hang. After enough consecutive failures, the circuit breaker trips open,
and further attempts fail *immediately* (no wasted connect-timeout wait) until the cooldown elapses and a
half-open probe succeeds. The client-facing contract never changes — it's a 503 either way — but the breaker
means `order-service` stops spending its own threads/connections waiting on a dependency it already knows is
down.

### Q: What happens if the *internal* thread pool handling that call is itself saturated, separate from the
downstream service being down?

**A:** This is a distinct failure mode from the above, and it's handled explicitly in this repo's own
code (not resilience4j): if `remoteCallExecutor`'s queue and pool are both full,
`ThreadPoolTaskExecutor.execute()` throws `RejectedExecutionException` **synchronously**, at the point of
submission — not inside the resulting future. `OrderPlacementService.submitRemote()` catches exactly this and
converts it into the same `DependencyUnavailableException` (503) every other failure mode produces, rather
than letting a raw `RejectedExecutionException` escape as an unhandled 500. The same pattern repeats for every
executor added this session (`OrderSagaDeadline`'s reaper simply skips a saturated order for the next pass;
`NotificationDispatcher` folds a rejected send into the existing retry/backoff machinery) — the common thread
is: a saturated pool should degrade the same way any other failure does, never as a special, worse case.
