# Configuration Properties & Profiles

### Q: `@ConfigurationProperties` vs `@Value` — when do you reach for each, and why does it matter for this
repo's config-refresh story?

**A:** `@Value("${some.property}")` injects a single value, read once at bean creation. `@ConfigurationProperties`
binds a whole prefixed group of properties onto a POJO's fields — `OrderProperties`
(`ecommerce.orders.*`: `sagaTimeout`, `reaperInterval`, `idempotencyKeyRequired`, ...) and
`NotificationProperties` (`ecommerce.notifications.*`) are both structured this way, with Lombok `@Getter`/
`@Setter` rather than individual `@Value` fields scattered across the classes that use them.

This distinction has a concrete, non-cosmetic consequence covered in the microservices guide's config-server
chapter: `@ConfigurationProperties` beans are automatically **rebound** when Spring Cloud Bus triggers a
refresh — no `@RefreshScope` annotation needed. A plain `@Value`-injected field is *not* refresh-aware unless
its containing bean is itself `@RefreshScope`. This is the practical reason this repo models anything meant to
be tunable live (saga timeouts, batch sizes, retry counts) as a `@ConfigurationProperties` class rather than a
scattering of `@Value` fields.

### Q: How does Spring Boot's profile-specific file naming actually work, and does this repo use any of the
fancier profile features (`spring.profiles.group`, `spring.config.activate.on-profile`)?

**A:** The plain, well-known convention only: `application-{profile}.yml` is picked up automatically whenever
that profile is active, layered over the profile-independent `application.yml` for overlapping keys (same
override semantics discussed for the centralized config-repo in the microservices guide — see
[microservices/02](../microservices/02-service-discovery-and-configuration.md)). This repo does **not** use
Boot's `spring.profiles.group` feature (composing one named profile out of several others) anywhere, and does
not use `spring.config.activate.on-profile` conditional document blocks either — profile composition, where it
happens at all, is done the blunt way: string-concatenating profile names onto `SPRING_PROFILES_ACTIVE` in the
Docker Compose/Kustomize overlay files themselves (e.g. `docker-compose.vault.yml` appends `,vault`;
`docker-compose.pki.yml` appends `,mtls`), not inside Spring configuration.

### Q: Walk through the `h2` profile — how is it meant to work, and does the actual code match the
documentation?

**A:** This is a good one to actually verify rather than take on faith, and it's a real example from this
repo's own inconsistencies — worth the habit of checking code against docs/comments rather than assuming
either is right. The `h2` profile (`application-h2.yml` in each service) is documented
(`README.md`) as "the zero-infrastructure escape hatch" — disabling Config Server, Eureka, and Vault so a
single service can run standalone with no external dependencies.

But: each service's **base** `application.yml` (the one active with *no* profile at all) defaults its
datasource to `jdbc:postgresql://localhost:5432/<db>` — Postgres, not H2. So running a bare `mvn
spring-boot:run` with nothing else set does **not** land you on the zero-infrastructure profile the docs
describe; `h2` only takes effect if you explicitly activate it (`SPRING_PROFILES_ACTIVE=h2` or
`-Dspring-boot.run.profiles=h2`), and no script or doc in the repo actually shows that activation step for a
local run. A stray comment in one Kubernetes manifest even claims the opposite ("the image's own classpath
`application.yml` already defaults to an in-process H2 database") — which doesn't match what that service's
actual `application.yml` contains. None of this breaks anything in practice (every documented *Maven/Compose*
workflow activates the right profile explicitly where it matters), but it's a legitimate, findable gap between
what a comment asserts and what the code does — exactly the kind of thing worth spotting rather than repeating
uncritically.

### Q: What's "relaxed binding," and why does it matter for environment variables in Docker/Kubernetes?

**A:** Spring Boot's property binder treats `my.property-name`, `my.propertyName`, `MY_PROPERTY_NAME`, and
`my.PROPERTY_NAME` as the same logical key — so a Kubernetes env var like `SPRING_CLOUD_STREAM_KAFKA_BINDER_BROKERS`
binds onto the property `spring.cloud.stream.kafka.binder.brokers` without any special translation code, purely
by Boot's own relaxed-binding rules (env vars conventionally use `SCREAMING_SNAKE_CASE` since shells don't
handle `.`/`-` well in variable names). Every env var in this repo's `docker-compose.yml`/`k8s/base/*.yaml`
relies on this — there's no custom `PropertySource` or translation layer; it's Boot's binder doing the
conversion automatically.

### Q: Every Maven-run test in this repo — unit or integration — activates the Spring profile `test`.
How, exactly?

**A:** Not via any `@ActiveProfiles` scattered across test classes (though some use that too) but centrally,
in the root `pom.xml`'s Surefire/Failsafe plugin configuration: a `systemPropertyVariable` hardcodes
`spring.profiles.active=test` for every test JVM Maven spins up, regardless of what any individual test class
declares. This is a different mechanism from the Maven `<profiles>` also defined in the same `pom.xml`
(`integration-test`/`integration-tests`) — those are **Maven build profiles** that just flip
`skipUnitTests`/`skipITs` properties to control which test phase runs; they have nothing to do with *Spring*
profiles despite the name collision, which is worth being precise about if asked — "Maven profile" and "Spring
profile" are two completely unrelated concepts that happen to share a name.
