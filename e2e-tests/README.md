# E2E Tests (Karate)

Karate features exercising the live, **already-running** Docker Compose
stack over real HTTP - no mocks, no Spring context, nothing started by this
module itself.

## Scenarios

[`customer-journey.feature`](src/test/resources/e2e/customer-journey.feature):

1. Log in (gets a JWT)
2. Create a customer
3. See the product catalogue (Inventory Service)
4. Create an order for a catalogue item
5. Invoke the Payment Service for that order
6. Poll Notification Service until it shows both the order-created and
   payment-processed notifications it reacted to over Kafka

[`resilience.feature`](src/test/resources/e2e/resilience.feature): the
gateway's circuit breaker (stop/restart payment-service, confirm it opens
then recovers) and rate limiter (burst past a route's per-minute budget).
Needs `docker` on PATH - see [DockerControl](src/test/java/e2e/DockerControl.java).

[`compensating-transaction.feature`](src/test/resources/e2e/compensating-transaction.feature):
the choreography saga's rollback path. Orders more stock than Inventory
Service has on hand, which fails the forward transaction mid-saga, then
confirms the compensating transaction actually undoes the order that was
already created - CANCELLED status, inventory left untouched (nothing was
ever reserved), and Payment Service never reached.

[`transactional-rollback.feature`](src/test/resources/e2e/transactional-rollback.feature):
a single service's own `@Transactional` rollback, as opposed to the
cross-service saga rollback above. A second payment for the same order
hits a DB unique constraint partway through `PaymentService.processPayment`,
and the whole transaction must roll back cleanly - a clean 400 business
error, and the original payment left completely untouched rather than
partially overwritten. Uses a synthetic orderId so it's fully isolated from
the Kafka saga (no race with PaymentEventListener's own automatic payment).

## Prerequisites

Start the stack first (see [docs/SETUP_AND_DEPLOYMENT.md](../docs/SETUP_AND_DEPLOYMENT.md)):

```bash
docker compose up -d
```

Either the default H2 profile or `--profile postgres` works. HTTPS/observability
profiles are irrelevant here (the test talks to the gateway on plain HTTP).

The login step authenticates as `karate_admin`, a test-only ADMIN account
seeded by a Flyway migration into every service's own local `users` table
(see [../db/README.md](../db/README.md)) - ADMIN satisfies every role check
used by this scenario (customers, orders, payments, inventory).

## Running it

```bash
mvn -f e2e-tests/pom.xml test -Dtest=CustomerJourneyRunner
mvn -f e2e-tests/pom.xml test -Dtest=ResilienceRunner
mvn -f e2e-tests/pom.xml test -Dtest=CompensatingTransactionRunner
mvn -f e2e-tests/pom.xml test -Dtest=TransactionalRollbackRunner
```

This module is **not** wired into the root reactor (`pom.xml`'s `<modules>`)
and none of the `*Runner` classes are named `*Test`/`*IT` - so none of them
run as a side effect of `mvn clean install` or any other normal build, here
or in any other service's Dockerfile. Run them explicitly, after the stack
is up, exactly as above.

## Notes

- Override the target URLs with `-Dgateway.url=... -Dnotification.url=...`
  if the gateway or notification-service aren't on `localhost:8080`/`:8086`.
- The scenario creates a fresh customer (UUID-suffixed name/email) on every
  run, so it's safe to run repeatedly without cleanup.
- If you see a `503` from the gateway right after rebuilding/restarting
  services, that's Resilience4j's circuit breaker tripped from earlier
  failed requests (its state is in-memory) - restart `api-gateway` and retry.
