# Actuator & Health Checks

### Q: What actuator endpoints are actually exposed here, and why not just expose everything Actuator
offers?

**A:** `health`, `prometheus`, `info` (`management.endpoints.web.exposure.include`), with
`management.endpoint.health.show-details: never` additionally keeping even the health endpoint's *details* (which
specific component failed, any embedded connection info) out of the response body — callers see only overall
up/down. Actuator ships endpoints that can dump environment variables, running threads, or trigger a heap dump;
exposing those by default (even just inside the mesh, without their own access control) is a real
information-disclosure and denial-of-service surface, so the exposure list here is kept to exactly what
automated health checks, Prometheus scraping, and the config-refresh mechanism need — nothing exploratory or
debug-oriented left reachable by default.

### Q: How do you add a custom component to Spring Boot's `/actuator/health` output, and does this repo do
it?

**A:** Implement `HealthIndicator` (or the newer `HealthContributor`/reactive variants) as a `@Component` —
Boot auto-discovers it and folds its result into the overall health aggregate automatically, named after the
bean (minus the `HealthIndicator` suffix). `CacheHealthIndicator` (`common/.../health/`) does exactly this: it
writes a timestamped key to Redis, reads it back, deletes it, and returns `Health.up()`/`Health.down()` with
`withDetail(...)`/`withException(...)` depending on whether the round trip succeeded — a genuine functional
probe of the cache, not just "is a bean present." This becomes the `cache` entry under `/actuator/health`
automatically, with zero wiring beyond the class existing as a `@Component`.

### Q: Does this repo define any fully custom actuator endpoints (not just a health contributor)?

**A:** No — worth knowing the distinction even when the answer is "it doesn't do this." A custom
`HealthIndicator` only *contributes to* the existing `/actuator/health` endpoint; a custom `@Endpoint`/
`@RestControllerEndpoint`/`@WebEndpoint` would be an entirely new actuator endpoint at its own path, with its
own operations. This repo has zero of those — every actuator surface here is one of Boot's own built-ins
(`health`, `prometheus`, `info`, plus `busrefresh` on `config-server` specifically for Spring Cloud Bus), never
a bespoke one.

### Q: What's the difference between a `HealthIndicator` like the Redis one above, and the liveness/readiness
probe groups Kubernetes actually polls?

**A:** They're related but answer different questions, and conflating them is a common mistake. Spring Boot
Actuator's **liveness/readiness health groups** (`/actuator/health/liveness`, `/actuator/health/readiness`) are
what Kubernetes probes poll directly (see
[microservices/12](../microservices/12-deployment-and-scaling.md)) — liveness asks "should this process be
killed and restarted," readiness asks "should traffic be routed here right now." A plain custom
`HealthIndicator` like `CacheHealthIndicator` contributes to the *overall* `/actuator/health` aggregate — but,
by Spring Boot's own default behavior, that's a **separate** aggregate from the liveness/readiness groups.
Boot's `liveness`/`readiness` groups default to only the built-in `livenessState`/`readinessState` indicators
(simple application-availability signals), not an automatic rollup of every custom `HealthIndicator` in the
context; a custom indicator only affects those groups if you explicitly add it via
`management.endpoint.health.group.readiness.include` (not done anywhere in this repo's config). That's a
deliberate split Boot itself made, not something this repo had to configure its way out of — and it's the
right default for exactly this case: `CacheHealthIndicator` failing (Redis down) is valuable to *see* on the
general health endpoint, but shouldn't restart the pod (the application itself is fine — see
[08](08-caching-abstraction.md) on this repo's cache-outage isolation design, where a down cache degrades
gracefully rather than being a fatal condition) or even pull it out of traffic rotation, since every other
request path remains fully functional with Redis down.
