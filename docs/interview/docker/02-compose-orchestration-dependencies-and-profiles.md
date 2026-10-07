# Compose Orchestration, Dependencies & Profiles

### Q: `depends_on` in Compose has two modes — just ordering, or actually waiting for health. Which does this
repo use, and why does the difference matter?

**A:** The health-aware form, consistently: every service's `depends_on` entry specifies
`condition: service_healthy`, not just a bare service name. Plain `depends_on: [postgres]` only guarantees
Docker **starts** the `postgres` container first — it says nothing about whether Postgres has actually finished
initializing and is ready to accept connections yet, which for a database can take meaningfully longer than
"the container process started." `condition: service_healthy` instead makes a dependent service wait until the
dependency's own `healthcheck:` genuinely reports healthy — e.g. order-service's `depends_on` block blocks on
Postgres, Kafka, *and* Redis all reporting healthy before order-service's own container even starts. Without
this, a service could start, immediately fail its first few connection attempts against a database that
technically exists but isn't accepting connections yet, and depending on the service's own retry/backoff
tolerance, either recover after some retries or fail outright — `condition: service_healthy` removes that race
entirely rather than hoping retry logic papers over it.

### Q: Give a concrete healthcheck example from this repo and explain why it's written the way it is.

**A:** Redis's: `redis-cli -a "$$REDIS_PASSWORD" --no-auth-warning ping | grep -q PONG` — note the **doubled**
`$$` (Compose's own escaping for a literal `$` inside a shell command passed through YAML, so the shell
actually sees `$REDIS_PASSWORD`, not Compose trying to substitute it a second time itself). It authenticates
*with* the real password — this repo's Redis requires auth unconditionally (`requirepass`), so a healthcheck
that didn't also authenticate would get an auth-required error back from Redis and misreport "unhealthy" even
when Redis is working completely correctly. Kafka's healthcheck similarly runs a real client command
(`kafka-broker-api-versions.sh --bootstrap-server localhost:9092`) rather than, say, just checking the port is
open — a port being open doesn't prove the broker has actually finished initializing its internal metadata and
is ready to serve real client requests.

### Q: What's `profiles:` doing on the `nginx`, `prometheus`, `grafana`, etc. service definitions, and why not
just always run them?

**A:** Compose profiles make a service **opt-in** — it's defined in the file but doesn't start on a plain
`docker compose up`; you have to explicitly request its profile (`docker compose --profile observability up`,
`--profile https up`). This repo uses it for genuinely optional capability groups: `"https"` gates the nginx
TLS-termination layer (only relevant if you're exercising HTTPS locally), `"observability"` gates the entire
Prometheus/Grafana/exporter/Loki stack (useful for dashboards, unnecessary noise and resource cost for a quick
functional test or the e2e suite). The alternative — always running everything — would make the default `up`
slower to start and heavier on resources for every single use case, even the ones that have no use for
dashboards or HTTPS termination at all.

### Q: Walk through the config-server's `docker-entrypoint.sh` — what real problem does it solve, and why is
it necessary at all given config-server's `config-repo` is already a real git-tracked directory in this
repository?

**A:** Spring Cloud Config Server's git backend needs its `config-repo` directory to behave as an actual git
**working copy** at runtime (it runs `git show`/`git log` internally to resolve a requested label/branch) — not
merely a directory that happens to contain the right files. This repo's `config-repo/` *is* tracked normally in
the outer repository (deliberately, so every clone and every pull-request diff shows the real config content —
see [the microservices config chapter](../microservices/02-service-discovery-and-configuration.md)), but the
*image* only gets a plain copy of those files baked in by the Dockerfile's `COPY`, with no `.git` directory of
its own — and Compose's bind-mount path (`./config-repo:/config-repo`) is the same: real files, no git history
riding along with them.

The entrypoint script bridges that gap on first run: it checks whether `.git` already exists in either the
image-baked path (`/app/config-repo`) or the bind-mounted path (`/config-repo`) and, if not, runs `git init`,
configures a commit identity, and makes one initial commit — giving config-server a genuine git repository to
query, with zero manual setup step required on any host. A restart finds `.git` already present and skips
straight to `exec "$@"` (running the actual `java -jar` command) — the whole script is idempotent by
construction, a no-op after the very first container start.

### Q: Why does the script check *two* different paths (`/app/config-repo` and `/config-repo`) instead of
just one?

**A:** Because the actual path differs depending on *how* the container is run, and the script doesn't assume
which one applies: `/app/config-repo` is what the Dockerfile's own `COPY` bakes into the image (what
Kubernetes gets, and what the classpath's default git URI points at), while `/config-repo` is the separate path
`docker-compose.yml` bind-mounts instead (overriding config-server's git URI to match it). Rather than needing
two different entrypoint scripts — or one that has to know in advance which deployment path it's running
under — `init_if_needed` is written to be a harmless no-op (`[ -d "$dir" ] || return 0`) for whichever path
*doesn't* apply in a given environment, so one script correctly handles both without branching on which
environment it's in.
