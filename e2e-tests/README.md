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

[`dlq-routing.feature`](src/test/resources/e2e/dlq-routing.feature): the
saga listeners' Dead Letter Queue safety net (DlqPublisher). Publishes a
hand-crafted, deliberately malformed event straight onto a main topic via
[KafkaFaultInjector](src/test/java/e2e/KafkaFaultInjector.java) (a raw
Kafka client, bypassing every service's own producer), making the real
`@KafkaListener` method throw in its actual business logic, then confirms
the event lands in that topic's `<topic>-dlq` instead of being silently
dropped. Covers 5 of the saga's 8 listener methods - the other 3 are
guarded against this kind of data-only fault injection by design (see the
feature file's own header for which, and why).

## Data showcase

Every scenario above prints a live snapshot of the exact rows it just
created or changed - straight from each service's own Postgres database,
for the specific IDs that run produced - right after the HTTP assertions
that confirm them. For example, `customer-journey.feature` prints the new
customer row, the order's PENDING→COMPLETED transition, the payment row,
the notification rows, and the inventory row before/after, every single
run. See [DataShowcase](src/test/java/e2e/DataShowcase.java): it connects
over JDBC to postgres's host-exposed port (5432) and renders a compact,
aligned table for whatever columns the calling step's SQL selects - so a
Karate HTML report or a terminal run both show exactly what landed in the
database, not just what the API echoed back. Best-effort: if postgres
isn't reachable (e.g. the H2 profile is running instead), it prints a note
and the scenario continues unaffected - this is diagnostic output, never
an assertion.

`dlq-routing.feature` uses the same class's `showRaw` to print the actual
Kafka message bodies instead (the input crafted event and what landed in
the DLQ topic), since that scenario's data lives in Kafka, not Postgres.

## Prerequisites

Start the stack first (see [docs/SETUP_AND_DEPLOYMENT.md](../docs/SETUP_AND_DEPLOYMENT.md)):

```bash
docker compose --profile postgres --env-file .env.postgres up -d
```

The HTTP assertions in every scenario work the same under the default H2
profile, but the data showcase above needs the `postgres` profile - H2 is
in-memory per-service and has no container/port for DataShowcase to query.
HTTPS/observability profiles are irrelevant here (the test talks to the
gateway on plain HTTP).

The login step authenticates as `karate_admin`, a test-only ADMIN account
seeded by a Flyway migration into every service's own local `users` table
(see [../db/README.md](../db/README.md)) - ADMIN satisfies every role check
used by this scenario (customers, orders, payments, inventory).

## Running it

Run everything in one go:

```bash
mvn -f e2e-tests/pom.xml test -Dtest='*Runner'
```

Surefire's `-Dtest` wildcard matches every `*Runner` class, so this picks up
new scenarios automatically as they're added - no need to update this list.
Scenario order matters for `resilience.feature` internally (see its own
header comment), but the five feature files themselves have no ordering
dependency on each other and are safe to run together like this.

Or run one feature at a time:

```bash
mvn -f e2e-tests/pom.xml test -Dtest=CustomerJourneyRunner
mvn -f e2e-tests/pom.xml test -Dtest=ResilienceRunner
mvn -f e2e-tests/pom.xml test -Dtest=CompensatingTransactionRunner
mvn -f e2e-tests/pom.xml test -Dtest=TransactionalRollbackRunner
mvn -f e2e-tests/pom.xml test -Dtest=DlqRoutingRunner
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
