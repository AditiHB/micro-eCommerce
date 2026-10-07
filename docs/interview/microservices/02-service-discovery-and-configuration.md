# Service Discovery & Configuration

### Q: What problem does service discovery solve, and how does Eureka solve it here?

**A:** In a scaled-out system, instances of a service come and go constantly — autoscaling, rolling deploys,
crashes — so a hardcoded `order-service:8082` address is wrong the moment anything changes. Service discovery
is a registry that answers "which instances of X are alive right now?"

`discovery-server` is Netflix Eureka (`infrastructure/discovery-server/pom.xml` →
`spring-cloud-starter-netflix-eureka-server`), configured as the registry itself — `register-with-eureka: false`,
`fetch-registry: false` (`config-repo/discovery-server.yml`), since it doesn't need to register with itself.
Every business service is a Eureka **client**: it registers on startup and sends periodic heartbeats; if
heartbeats stop, Eureka eventually evicts that instance from the registry.

### Q: What's "client-side load balancing," and where does it show up in this repo?

**A:** Server-side load balancing means a dedicated load balancer sits in front of instances and routes traffic.
Client-side means the *caller* asks the registry for the current list of healthy instances and picks one
itself — no extra hop, no extra infrastructure component in the request path.

`api-gateway`'s routes use exactly this: `uri: lb://order-service`, `lb://inventory-service`, etc.
(`infrastructure/api-gateway/src/main/resources/application.yml`). The `lb://` scheme tells Spring Cloud
Gateway to resolve `order-service` via Eureka at request time and load-balance across however many instances
are currently registered — the gateway never knows or cares how many replicas exist.

### Q: What is Spring Cloud Config Server, and why centralize configuration instead of each service keeping
its own `application.yml`?

**A:** Before centralization, this repo had every service's profile files (`application-postgres.yml`,
`application-oracle.yml`, `application-mtls.yml`, etc.) duplicated across five services, with the actual
`config-server` sitting unused (`config-repo/` was empty — a no-op). Centralizing moved genuinely shared
configuration (actuator exposure, logging pattern, JWK/issuer/audience, the Hikari/JPA tuning block that was
byte-for-byte identical across five services' `application-postgres.yml`) into one git-backed repo that
`config-server` serves over HTTP, with Basic Auth. See [`docs/CONFIG_SERVER.md`](../../CONFIG_SERVER.md) for the
full design.

The config-repo resolves in a fixed precedence order, each layer overriding the previous for overlapping keys:

1. `application.yml` — global, every client, every profile
2. `application-{profile}.yml` — global, per-profile (e.g. `application-postgres.yml`)
3. `<service-name>.yml` — per-app, every profile
4. `<service-name>-{profile}.yml` — per-app, per-profile

One subtlety worth knowing: **list-valued properties are replaced wholesale, never merged**, across these
layers. `spring.autoconfigure.exclude` declared in a service's base file is *fully overwritten* by the same key
declared in a profile file — if you need both sets of exclusions, you have to repeat them, not just add to
them.

### Q: Doesn't requiring config-server create a chicken-and-egg problem — how do you develop locally without
it running?

**A:** This is solved with two independent mechanisms:

- `spring.config.import: "optional:configserver:http://localhost:8888/"` — the `optional:` prefix means a
  service tolerates config-server being unreachable and just runs on its local, bundled config instead of
  refusing to start.
- A separate `spring.cloud.config.fail-fast` property (`false` by default, flipped to `true` only via the
  `SPRING_CLOUD_CONFIG_FAILFAST` env var in Compose/Kubernetes) gives you *real* enforcement in deployed
  environments — config-server genuinely must be reachable there — without breaking the "resource not found is
  tolerated" behavior `optional:` gives you for solo local development.
- The `h2` profile (already this project's zero-infrastructure profile for `mvn spring-boot:run`) additionally
  sets `spring.cloud.config.enabled: false`, `spring.cloud.discovery.enabled: false`, and
  `spring.cloud.vault.enabled: false` — so running a single service locally against H2 never even attempts to
  reach config-server, Eureka, or Vault.

Why not just make the import mandatory and catch the failure? Spring Boot's Config Data API resolves
profile-independent imports in an earlier phase than profile-specific documents — a mandatory import declared
in the base file **cannot** be rescued by `spring.cloud.config.enabled: false` set in a profile file, because
that profile file hasn't even been read yet when the mandatory import is resolved. This was hit for real in
this repo (see git history on the config-centralization branch) and is why the two independent flags above
exist instead of one "smarter" one.

### Q: How does a config value change get applied to already-running instances without a restart?

**A:** Spring Cloud Bus, wired over the same Kafka broker every service already talks to — no new message
broker introduced. `config-server` depends on `spring-cloud-config-monitor` + `spring-cloud-starter-bus-kafka`,
and exposes `/actuator/busrefresh`. A `POST` there broadcasts a refresh event to every connected service
instance over Kafka, and each one re-fetches its config from config-server.

What actually updates on refresh depends on how the value is consumed:

- `@RefreshScope` beans are destroyed and recreated with the new values — fully refresh-aware.
- A plain `@Value`-injected field is **not** refresh-aware by default unless its containing bean is
  `@RefreshScope`.
- `@ConfigurationProperties` beans *are* automatically refresh-aware without needing `@RefreshScope` (Spring
  Boot rebinds them on a refresh event) — this is why properties classes like `OrderProperties`/
  `NotificationProperties` in this repo are the right place for anything you want to be able to tune live.
