# Workloads & Deployments

### Q: Why does this repo use `Deployment` for every business service but `StatefulSet` specifically for
Postgres?

**A:** `Deployment` assumes every replica is **interchangeable** — identical, stateless, and freely
replaceable in any order, which is exactly true of `order-service`/`inventory-service`/etc. (any of the 2-5
pods can handle any request, and killing one and starting a fresh one changes nothing about correctness).
`StatefulSet` exists for workloads where identity and storage genuinely matter per instance — a stable,
predictable network identity (`postgres-0`, not an arbitrary generated pod name) and its **own** persistent
volume that survives a pod restart and reattaches to the *same* pod identity, not a randomly assigned one. A
database is the textbook case: Postgres's actual data directory has to persist and stay associated with the
same instance across restarts, which `Deployment`'s interchangeable-replica model doesn't provide at all (a
`Deployment`'s pods are meant to be disposable and anonymous relative to each other).

### Q: Every business service manifest sets `replicas: 2` as its baseline, even before the HPA does anything
— why 2, not 1?

**A:** `replicas: 1` has no redundancy at all — a single pod restarting (a crash, a rolling update, a node
being drained) means a real, if brief, availability gap for that service. `replicas: 2` is the minimum that
actually buys you something: during a rolling update or a single pod's restart, the other replica keeps serving
traffic the whole time, and a single node failure (in a multi-node cluster) doesn't necessarily take the whole
service down if the two replicas are scheduled on different nodes. This is independent of, and prior to,
whatever the HPA later does — `minReplicas: 2` on the HPA (see
[02](02-scaling-and-resource-management.md)) is really just codifying the same floor the base Deployment
manifest already set.

### Q: Kubernetes gives you a `Service` object for stable addressing *inside* the cluster — doesn't that make
this repo's own Eureka service discovery (see
[../microservices/02](../microservices/02-service-discovery-and-configuration.md)) redundant?

**A:** They overlap in *purpose* (both answer "how do I reach a live instance of X") but operate at different
layers and were designed for different deployment targets — worth being precise about rather than treating
them as interchangeable. A Kubernetes `Service` is cluster-native: it only works because Kubernetes' own
internal DNS and kube-proxy are present, which means this application's service-discovery mechanism would have
to change entirely if it were ever run somewhere that isn't Kubernetes (the whole reason this repo *also* runs
via plain Docker Compose, with no Kubernetes involved at all, and Eureka works identically in both). Eureka is
**application-level and deployment-target-agnostic** — the exact same `lb://order-service` client-side
load-balancing works whether the underlying instances are Kubernetes pods, Compose containers, or bare VMs,
because Eureka doesn't know or care which. In this specific deployment, both genuinely exist simultaneously:
Kubernetes' Service objects give each Deployment a stable cluster-DNS name other things (like the database
connection string) rely on, while Eureka remains how the application layer's own `lb://` routing (api-gateway's
Spring Cloud Gateway routes) actually resolves instances.

### Q: What's `enableServiceLinks: false`, and why does every pod in this repo set it explicitly?

**A:** By default, Kubernetes injects a set of environment variables into every pod describing every
`Service` that existed in its namespace *at the time the pod was scheduled* (`<SERVICE_NAME>_SERVICE_HOST`,
`_PORT`, etc.) — a legacy mechanism that predates reliable cluster DNS. This repo disables it uniformly across
every pod specifically because of a real, concrete foot-gun it causes here: Kubernetes auto-injects a
`POSTGRES_PORT` environment variable once a `postgres` Service exists (because the variable-naming convention
is derived purely from the Service's name, with no awareness that `POSTGRES_PORT` happens to collide with a
variable name the Postgres Docker image's own entrypoint script reads for something else entirely). The actual
Postgres image doesn't honor it the way the auto-injection mechanism would suggest, but other tools in this
same class of "read an ambiguous, auto-injected env var" are known to genuinely misbehave on exactly this kind
of collision — disabling the whole mechanism uniformly, everywhere, sidesteps an entire class of "which
auto-injected variable silently collided with which application's own expected env var" bugs, rather than
special-casing it service by service only where it's currently known to bite.
