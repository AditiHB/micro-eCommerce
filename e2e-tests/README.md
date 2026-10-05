# E2E Tests (Karate)

Karate features exercising the live, **already-running** Docker Compose
stack over real HTTP - no mocks, no Spring context, nothing started by this
module itself. They go through the API gateway, authenticate against Keycloak, and use the same
`/api/v1/...` contract a real client uses.

## Scenarios

| Feature | What it proves |
|---|---|
| [`customer-journey`](src/test/resources/e2e/customer-journey.feature) | Create a customer and a product, place an order, and watch the choreography saga run: stock is reserved, the **real total** is charged, the order completes, and the customer is notified. Also a multi-line order priced line by line. |
| [`compensating-transaction`](src/test/resources/e2e/compensating-transaction.feature) | The saga's rollback paths. Not enough stock; one short line cancels the whole order (reservation is all-or-nothing); the payment processor declines (stock comes back, customer told); a completed order cannot be cancelled and its payment can be refunded exactly once. |
| [`inventory-idempotency`](src/test/resources/e2e/inventory-idempotency.feature) | Stock is never oversold, never negative, and the **last unit is sellable**. Redelivered events change nothing. Stale `If-Match` cannot overwrite a newer stock-take (412). |
| [`transactional-rollback`](src/test/resources/e2e/transactional-rollback.feature) | A rejected request leaves no order behind; the same `Idempotency-Key` returns the same order, however many times it is retried. |
| [`resilience`](src/test/resources/e2e/resilience.feature) | Stops **real containers**: orders are accepted while Kafka is down and complete when it returns (transactional outbox); a late cancellation still ends fully compensated; a dependency outage is a clean `503 DEPENDENCY_UNAVAILABLE`; the gateway answers 503/429 as problem documents. Needs `docker` on PATH ([DockerControl](src/test/java/e2e/DockerControl.java)). |
| [`dlq-routing`](src/test/resources/e2e/dlq-routing.feature) | Poison messages published straight onto the real topics ([KafkaFaultInjector](src/test/java/e2e/KafkaFaultInjector.java)): retried or dead-lettered, parked in the owning service's own database, listed and replayable by an admin, never blocking the partition. A hostile type header is refused. |
| [`error-handling`](src/test/resources/e2e/error-handling.feature) | One error contract: every failure from every service is an RFC 9457 `application/problem+json` with the same members and a stable `errorCode` (404, 400 + field errors, 405, 409, 412, 401). |
| [`authorization`](src/test/resources/e2e/authorization.feature) | A customer token is kept out of every back-office endpoint, sees and creates only their own data, cannot cancel someone else's order; dead letters are admin-only; the old login endpoint and service-name routes are gone. |
| [`rest-api-coverage`](src/test/resources/e2e/rest-api-coverage.feature) | One pass over every public endpoint (customers with `ETag`/`If-Match`, products incl. batch lookup and SKU, inventory, orders, payments, notifications) and the versioning contract: `/api/v1` is canonical, the old unversioned paths still work but carry a `Deprecation` header. |

The `helpers/` features (`create-customer`, `create-product`, `place-order`, `await-order`, ...) are
small reusable steps the scenarios call; they are not run on their own.

## Data showcase

Scenarios print a live snapshot of the exact rows they just created or changed, straight from each
service's own Postgres database, right after the HTTP assertions that confirm them
(see [DataShowcase](src/test/java/e2e/DataShowcase.java): it connects over JDBC to postgres's
host-exposed port and renders a compact table for whatever columns the calling step selects).
It is best-effort diagnostic output, never an assertion: if postgres is not reachable the scenario
carries on unaffected. It reads `POSTGRES_ADMIN_PASSWORD` from the environment.

## Prerequisites

Start the stack first (see [docs/SETUP_AND_DEPLOYMENT.md](../docs/SETUP_AND_DEPLOYMENT.md)):

```bash
scripts/gen-env.sh        # once: generates .env (secrets + test-user passwords)
docker compose up -d --build

# The suite reads its credentials from the environment - source your .env first:
set -a; . ./.env; set +a
```

PostgreSQL is the only database; there is no profile to select. The sign-in step gets a token from
**Keycloak** (`auth.feature`, password grant on the development-only `ecommerce-e2e` client) as
`karate_admin`, a test-only ADMIN user created by `infrastructure/keycloak/seed-dev.sh` with a password from
your `.env`. `authorization.feature` additionally signs in as `karate_user` (a plain customer bound to
customer 1) to prove what a customer may *not* do.

## Running it

Everything in one go:

```bash
mvn -f e2e-tests/pom.xml test -Dtest='*Runner' \
  -Dgateway.url=http://localhost:8080 -Dnotification.url=http://localhost:8086 -Dkeycloak.url=http://localhost:8180
```

Surefire's `-Dtest` wildcard matches every `*Runner` class, so new features are picked up automatically.
`resilience.feature` restarts containers, so run it on its own if you want to watch it:

```bash
mvn -f e2e-tests/pom.xml test -Dtest=CustomerJourneyRunner
mvn -f e2e-tests/pom.xml test -Dtest=CompensatingTransactionRunner
mvn -f e2e-tests/pom.xml test -Dtest=InventoryIdempotencyRunner
mvn -f e2e-tests/pom.xml test -Dtest=TransactionalRollbackRunner
mvn -f e2e-tests/pom.xml test -Dtest=ResilienceRunner
mvn -f e2e-tests/pom.xml test -Dtest=DlqRoutingRunner
mvn -f e2e-tests/pom.xml test -Dtest=ErrorHandlingRunner
mvn -f e2e-tests/pom.xml test -Dtest=AuthorizationRunner
mvn -f e2e-tests/pom.xml test -Dtest=RestApiCoverageRunner
```

This module is **not** wired into the root reactor and none of the runners are named `*Test`/`*IT`, so none
of them run as a side effect of `mvn clean install`. Run them explicitly, after the stack is up.

## Notes

- Every scenario creates fresh data (UUID-suffixed customers, products and SKUs), so it is safe to run
  repeatedly without cleanup.
- The gateway's rate limiter counter is per client IP and shared across routes. Each feature clears it in its
  `Background`; if you still see a `429`, clear it:
  `docker exec redis redis-cli -a "$REDIS_PASSWORD" --no-auth-warning eval "for _,k in ipairs(redis.call('keys','rate_limit:*')) do redis.call('del',k) end" 0`
- A `503` from the gateway right after restarting services is Resilience4j's circuit breaker (in-memory
  state) - give it its wait duration or restart `api-gateway`.
- Saga completion is asynchronous: scenarios poll (`helpers/await-order.feature`) rather than sleep.

## Known gaps

- Redis-backed caching correctness under a Redis outage is proven by the Java integration tests
  (`FailSafeCacheTest`, `*CacheIntegrationTest`), not here.
- `NotificationService`'s `FAILED` branch (the sender itself throwing) is covered by unit and integration
  tests; every scenario here only ever sees `SENT`.
- The Kafka HA overlay (`docker-compose.kafka-ha.yml`) and the Vault / step-ca overlays are validated by
  `docker compose config` and their own docs, not by this suite.
