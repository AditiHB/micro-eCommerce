# Architecture & Service Boundaries

### Q: How are services split in this repo, and what's actually running?

**A:** Five business services, each owning its own data and domain:

- `customer-service` — customer profiles
- `order-service` — order placement, the order saga's coordinator
- `inventory-service` — product catalogue **and** stock/reservations (merged, see below)
- `payment-service` — payment processing (simulated gateway)
- `notification-service` — customer notifications (email/log)

Plus three infrastructure services: `discovery-server` (Eureka), `config-server` (Spring Cloud Config), and
`api-gateway` (Spring Cloud Gateway) — the single entry point clients talk to. A `common` Maven module holds
shared, cross-cutting code (security config, outbox/event publishing, caching config, resilience utilities)
that every business service depends on.

### Q: What's the database-per-service pattern, and is it really enforced here?

**A:** Each service owns its data exclusively — no service reaches into another's tables directly; everything
crosses a service boundary through its API or through events. In this repo that's enforced at the database
level, not just by convention: `docker-compose.yml`'s `POSTGRES_MULTIPLE_DATABASES` environment variable
creates five separate databases (`customer_db`, `inventory_db`, `order_db`, `payment_db`, `notification_db`)
on one Postgres instance, each with its **own owner role** and credentials — `order-service`'s datasource
literally cannot authenticate against `inventory_db`.

Worth knowing as a tradeoff: this repo shares the *physical* Postgres instance across all five logical
databases (one pod, `k8s/overlays/postgres/postgres.yaml`). That's "database per service" in the data-isolation
sense (no cross-service queries, no shared schema, independent migrations per service), but it is **not**
infrastructure isolation — a noisy-neighbor service or a single Postgres outage affects everyone. A stricter
setup would give each service its own database *instance*.

### Q: How do you decide where a service boundary should go? Walk through a real example.

**A:** This repo actually did this the other way around once: it originally had a separate `product-service`
for the catalogue, which was later **merged into `inventory-service`**. The reasoning: product catalogue data
(name, price, SKU) and stock/inventory data were always read and written together in practice — pricing a
product always needed to know if it was in stock, and the two had no independent scaling or failure-isolation
need that justified the network hop and the duplicated deployment/ops overhead between them. When two
"services" always change together and are read together, that's a sign they're actually one bounded context
that was split too early — the fix isn't a design pattern, it's merging them back and deleting the now-unneeded
service.

The opposite signal is `payment-service` staying separate from `order-service` despite being tightly coupled in
the saga: payments have a genuinely different failure mode (a real payment gateway outage shouldn't block order
intake), a different compliance surface (PCI scope), and different scaling characteristics — so the network hop
and eventual consistency there are worth paying for.

### Q: Why have a separate API Gateway instead of letting clients call each service directly?

**A:** `api-gateway` (Spring Cloud Gateway) is the single entry point: one TLS/auth surface instead of five,
one place to apply rate limiting and circuit breakers uniformly, and one place to evolve routing without
touching every client. Routes are configured per-service with their own circuit breaker and rate limit
(`config-repo/api-gateway.yml`) and resolved via Eureka's client-side load balancing (`uri: lb://order-service`,
etc. — see [02](02-service-discovery-and-configuration.md)), so the gateway never hardcodes a service's host or
port, and adding a second replica of any service needs zero gateway changes.

### Q: Why have both a discovery server *and* a config server — aren't they solving the same problem?

**A:** No — they solve two different, orthogonal problems that both happen to be about "where do I find
things":

- **Config server** answers *"what are my settings?"* — centralizes `application.yml`/profile-specific
  properties so they're not duplicated five times, and so an operator can change one value in one place (see
  [02](02-service-discovery-and-configuration.md)).
- **Discovery server** (Eureka) answers *"where is a live instance of order-service right now?"* — a runtime
  registry of addresses that changes constantly as pods scale up/down, restart, or fail health checks.

Config is relatively static and fetched once at startup; service addresses are dynamic and queried
continuously. Conflating them would mean every service-address change requires a config change, which defeats
the point of dynamic scaling.

### Q: What's the risk of a shared `common` module across all microservices, and is it worth it here?

**A:** The classic risk is **coupling through the back door**: if every service depends on the same library,
a change to that library (even a "harmless" one) can require redeploying every service simultaneously, which is
exactly the kind of lockstep deployment microservices are meant to avoid. It can also become a dumping ground
that quietly re-centralizes logic that should have stayed per-service.

In this repo, `common` is deliberately scoped to things that are *infrastructure*, not *business logic*:
security config, the outbox/event-publishing mechanism, cache configuration, resilience utilities, test support
(`SharedPostgres`/`SharedRedis`). None of it encodes "how a customer is validated" or "how a payment is
processed" — that stays in each service. That's the line worth defending: a shared module is fine for plumbing
every service needs identically; it's a liability the moment it starts holding domain rules that differ, or
should be allowed to differ, per service.
