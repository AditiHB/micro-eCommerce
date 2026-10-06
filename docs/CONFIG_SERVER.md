# Spring Cloud Config Server

Externalized configuration is centralized in a Spring Cloud Config Server (`infrastructure/config-server`),
backed by a real git-tracked `config-repo/` at the root of this repository. This document is the design
reference every service's own comments point back to.

## Why

Every service used to carry its own copy of properties that were identical (or nearly identical) across
5-8 services - the Postgres/Hikari/JPA tuning, the mTLS SSL bundle wiring, the actuator/logging shape, the
JWK/issuer/audience values. `config-repo/` was wired up early in this project but left empty, so Config
Server existed in the architecture without doing anything. It's now populated for real.

## What lives where

Spring Cloud Config resolves four kinds of document for a client asking for `{application}` at profile
`{profile}`, each able to override the one before it:

1. **`config-repo/application.yml`** - global, every client, every profile. Common actuator/management
   shape, logging pattern, Eureka URL template, the public `ecommerce.security.*` JWK/issuer/audience
   values, Flyway/Kafka defaults, and the Spring Cloud Bus wiring.
2. **`config-repo/application-{h2,postgres,oracle,mtls}.yml`** - global, per-profile. The JPA/Hikari
   production tuning that was byte-for-byte identical across every Postgres-backed service; the mTLS SSL
   bundle wiring that's safe to apply uniformly (see the mTLS exception below).
3. **`config-repo/<service-name>.yml`** - per-app, every profile. What's genuinely unique to that service:
   its port, Kafka consumer group, datasource URL, and feature-specific config (payment's gateway/simulator
   settings, the gateway's entire route table, order-service's resilience4j instances, etc.).
4. **`config-repo/<service-name>-mtls.yml`** - per-app, per-profile. `server.ssl.bundle`/`client-auth`/the
   Eureka hostname block deliberately live here, duplicated across customer/order/inventory/payment-service,
   rather than in the global mTLS file - putting them there would silently turn on inbound mTLS for
   `notification-service` too, breaking its one documented exception (it stays directly, externally
   reachable; see `notification-service-mtls.yml`).

**Secrets never go into `config-repo/`.** Every property that moved there keeps the exact `${ENV_VAR:default}`
placeholder it already had - credentials still come from plain environment variables or HashiCorp Vault
(`common/application-vault.yml`, an entirely separate, untouched mechanism).

## Why every service's local `application.yml` still looks complete

Each service's own classpath `application.yml` is **not** trimmed down to "bootstrap-only" properties. It
stays a complete, self-sufficient set of defaults - the same shape the service had before this change -
because:

- A bare `mvn spring-boot:run` (no Docker stack) still needs to work.
- A `@WebMvcTest`/`@DataJpaTest` slice test, which may not activate any Spring profile at all, still goes
  through environment preparation and would otherwise hit an import it can't satisfy.
- The `h2` profile explicitly disables Config Server/Discovery/Vault (`spring.cloud.config.enabled: false`,
  mirroring `application-test.yml`'s existing convention) for the zero-infrastructure local-dev case.

Whenever config-server **is** reachable, its values simply override the local ones (config-server-supplied
properties outrank a service's own classpath `application*.yml`). The duplication this costs is deliberate -
removing it would mean a local or test run silently loses properties it used to have.

## Mandatory in real environments, optional everywhere else

`spring.config.import` stays `optional:configserver:http://config-server:8888/` in every service - the
`optional:` prefix is what lets a test slice or a bare local run continue if no server is listening at all.
Genuine enforcement ("don't silently start on stale local defaults") comes from a **separate** property,
`spring.cloud.config.fail-fast`, which is `false` by default and set to `true` only by Docker Compose and
Kubernetes (`SPRING_CLOUD_CONFIG_FAILFAST=true`). The distinction matters: `optional:` governs whether Boot
tolerates *not finding a resolver or resource* at all; `fail-fast` governs what happens once a connection is
actually attempted and fails (after a few retries - `spring.cloud.config.retry.*`) - and it overrides the
`optional:` tolerance once that happens. A service that can't reach config-server in Compose/Kubernetes
fails to start; the same service with no profile active in a unit test, or running with the `h2` profile,
never attempts the connection at all and isn't affected either way.

Every client authenticates to config-server's HTTP Basic Auth with `CONFIG_SERVER_USERNAME`/
`CONFIG_SERVER_PASSWORD` (the password already existed as a generated secret - `scripts/gen-env.sh` - but
was never actually wired to a consumer before this).

## Live refresh via Spring Cloud Bus

Every config-client service (the 5 business services, api-gateway, discovery-server) and config-server
itself carry `spring-cloud-starter-bus-kafka`, broadcasting over the same Kafka broker every service already
uses for domain events (a separate topic, `springCloudBus`, provisioned by Spring Cloud Stream's own binder
admin client - independent of the broker's disabled implicit auto-creation). To push a change:

1. Edit a file under `config-repo/`, commit it (or let the entrypoint's self-init handle the very first
   commit - see below).
2. `POST` to config-server's `/monitor` endpoint (from `spring-cloud-config-monitor`) - or just
   `POST /actuator/busrefresh` directly on config-server, or any one running instance; Spring Cloud Bus
   relays it to every connected instance regardless of which one you hit.

**What actually refreshes without a restart**: Spring's `@ConfigurationProperties` beans rebind
automatically on every bus refresh (no annotation needed). A plain `@Value`-injected field does **not** -
only a bean marked `@RefreshScope` does. This project does not currently mark any bean `@RefreshScope`
speculatively; the refresh mechanism is real and working end-to-end (verified by changing a `logging.level.*`
value, which Boot's `LoggingSystem` always picks up live), but a given `@Value`-bound property will only take
effect on its next natural reload (a redeploy) unless its bean is later annotated `@RefreshScope`.

## `config-repo/` as a real git repository

Config Server's git backend needs `config-repo/` to actually *be* a git working copy (it runs `git show`/
`git log` to resolve a label), not just a directory of files. The files themselves are ordinary tracked
content in this repository - reviewable in a PR, present for anyone who clones - with no nested `.git`
committed into the outer repository. `infrastructure/config-server/docker-entrypoint.sh` gives the directory
real git history the first time the config-server container ever starts (idempotent - a restart finds
`.git` already there and skips straight to the JVM), covering both delivery paths: Kubernetes' image-baked
`/app/config-repo` and Docker Compose's bind-mounted `/config-repo`.

## What didn't move

- `common/application-vault.yml` and `infrastructure/api-gateway/application-vault.yml` - the `vault`
  profile's secret-delivery mechanism, orthogonal to Config Server.
- `spring.security.user.*` on discovery-server - its own dashboard's Basic Auth; it's what every other
  client authenticates against, so it can't depend on Config Server being up.
- resilience4j configuration - order-service/notification-service's in-process retry/circuit-breaker windows
  and api-gateway's edge-level ones are deliberately different by design (see `docs/RESILIENCE.md`'s
  timeout-nesting model), not accidental duplication; unifying them would silently change behavior.
