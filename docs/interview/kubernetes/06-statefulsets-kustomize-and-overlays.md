# StatefulSets, Kustomize & Overlays

### Q: What does `volumeClaimTemplates` actually do on the Postgres `StatefulSet`, and how is it different
from a plain `volumes`/`volumeMounts` pair on a `Deployment`?

**A:** `volumeClaimTemplates` (`k8s/overlays/postgres/postgres.yaml`: `accessModes: ["ReadWriteOnce"]`,
`storage: 2Gi`) has Kubernetes automatically create a **separate, dedicated** `PersistentVolumeClaim` per
replica of the `StatefulSet`, each one stably bound to that specific ordinal pod (`postgres-0` gets its own PVC,
which persists and reattaches to `postgres-0` specifically across restarts/rescheduling) — this is only
meaningful because `StatefulSet` pods have stable, predictable identities in the first place (see
[01](01-workloads-and-deployments.md)). A plain `Deployment` referencing one shared `volume` would either mean
every interchangeable replica fights over the *same* volume (fine for genuinely shared, concurrent-safe
storage; actively wrong for a single database's own data directory) or, more commonly, just wouldn't attempt
per-replica persistent storage at all — which is exactly why this repo's business services (truly
interchangeable, stateless) use `Deployment` with no persistent volumes, while Postgres (exactly one replica
here, but modeling the pattern correctly regardless) uses the `StatefulSet` + `volumeClaimTemplates` combination
built for this.

### Q: Describe Kustomize's base-plus-overlays structure as this repo actually uses it — what's `base`, and
what does an overlay actually do to it?

**A:** `k8s/base/` is the common foundation — every Deployment, Service, the HPA set, RBAC — assembled via
`k8s/base/kustomization.yaml`'s `resources:` list. An overlay (`k8s/overlays/postgres`, `/mtls`, `/h2`) is a
*separate* `kustomization.yaml` whose own `resources:` list starts with `../../base` (pulling in the whole
base set unmodified) and then layers additional resources and/or **strategic merge patches** on top. The
Postgres overlay is the clearest example: it adds `postgres.yaml` (the StatefulSet itself, which doesn't exist
in `base` at all) and patches five existing Deployments from `base` — `customer-service`, `order-service`, etc. —
adding the `SPRING_PROFILES_ACTIVE: postgres`, `DB_HOST`, `DB_USERNAME`, `DB_PASSWORD` environment variables
each one needs to actually connect to the newly-added Postgres instance, without needing to maintain five
entirely separate, fully-duplicated Deployment manifests just to express "run with Postgres instead." One base,
several composable overlays, each describing only its own *delta* from the shared foundation.

### Q: The `h2` overlay's own comment claims applying it leaves services falling back to "an in-process H2
database" via their classpath defaults — is that actually what happens if you apply it today?

**A:** This is worth walking through rather than taking the comment's word for it, and it's a genuinely good
example of checking an assumption against the real config rather than trusting a comment that sounds
authoritative. `k8s/overlays/h2/kustomization.yaml` really does apply **zero patches** — just `../../base`,
unmodified. But `k8s/base`'s own Deployment manifests set **no** `SPRING_PROFILES_ACTIVE` at all (confirmed by
grep — zero matches in `09-order-service.yaml`), and each service's actual classpath `application.yml` default
datasource is `${DB_URL:jdbc:postgresql://${DB_HOST:localhost}:${DB_PORT:5432}/order_db}` — **Postgres**, not
H2, when no override is supplied. So applying `kubectl apply -k k8s/overlays/h2` today would leave every
service with no active profile, trying to reach Postgres at `localhost:5432` **from inside its own pod** — with
no Postgres Service deployed in this overlay at all, and `localhost` inside a pod referring only to that pod
itself. That would fail to connect, not quietly and successfully fall back to H2 as the overlay's own comment
asserts.

This is a genuinely useful, confirmed finding, not a hypothetical: a comment that sounds confident and
specific can still be stale relative to the code sitting right next to it, and the only way to actually know is
to check the real property value and the real env-var list — exactly what resolved this one. (The equivalent
question for local `mvn spring-boot:run`, where the same kind of claim shows up again, is covered in the
[Spring Boot guide](../spring-boot/03-configuration-properties-and-profiles.md).)

### Q: How does the `mtls` overlay differ mechanically from the `postgres`/`h2` overlays — what kind of
resources does *it* add?

**A:** Certificate management resources, via cert-manager: a self-signed `ClusterIssuer`
(`k8s/overlays/mtls/cluster-issuer.yaml`) and per-service `Certificate` objects
(`k8s/overlays/mtls/certificates.yaml`) that cert-manager watches and uses to actually issue and continuously
renew TLS certificates for each service, stored as Kubernetes `Secret`s the Deployments then mount. This is a
different *kind* of overlay delta than the Postgres one's "patch existing Deployments' env vars" pattern — it's
closer to "add a whole new category of managed resource that something else in the cluster (cert-manager, a
separately installed operator) actively reconciles over time," rather than a one-time static config patch. Both
are legitimate overlay patterns; which one a given concern needs depends on whether the thing you're adding is
static configuration or an ongoing, actively-managed resource.
