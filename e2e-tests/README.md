# E2E Tests (Karate)

A single Karate feature exercising the full customer journey against the
**already-running** Docker Compose stack over real HTTP - no mocks, no
Spring context, nothing started by this module itself.

## Scenario

[`customer-journey.feature`](src/test/resources/e2e/customer-journey.feature):

1. Log in (gets a JWT)
2. Create a customer
3. See the product catalogue (Inventory Service)
4. Create an order for a catalogue item
5. Invoke the Payment Service for that order
6. Poll Notification Service until it shows both the order-created and
   payment-processed notifications it reacted to over Kafka

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
```

This module is **not** wired into the root reactor (`pom.xml`'s `<modules>`)
and `CustomerJourneyRunner` is deliberately *not* named `*Test`/`*IT` - so it
never runs as a side effect of `mvn clean install` or any other normal build,
here or in any other service's Dockerfile. Run it explicitly, after the
stack is up, exactly as above.

## Notes

- Override the target URLs with `-Dgateway.url=... -Dnotification.url=...`
  if the gateway or notification-service aren't on `localhost:8080`/`:8086`.
- The scenario creates a fresh customer (UUID-suffixed name/email) on every
  run, so it's safe to run repeatedly without cleanup.
- If you see a `503` from the gateway right after rebuilding/restarting
  services, that's Resilience4j's circuit breaker tripped from earlier
  failed requests (its state is in-memory) - restart `api-gateway` and retry.
