# Deployment & Scaling

### Q: What's the role of Docker Compose versus Kubernetes in this repo — why have both?

**A:** Different stages of the same system's lifecycle, not competing choices. Compose is the fast,
single-machine inner-loop: `docker compose up` brings up the whole stack (Postgres, Kafka, Redis, Keycloak, and
every service) for local development and the Karate e2e suite, with opt-in overlay files
(`docker-compose.pki.yml`, `docker-compose.vault.yml`, `docker-compose.kafka-ha.yml`) layering in mTLS, Vault,
or a 3-broker Kafka cluster only when you actually want to exercise those. Kubernetes (`k8s/base` +
environment-specific overlays) is the actual deployment target — horizontal autoscaling, rolling updates,
self-healing via liveness/readiness probes, and real multi-node resource scheduling, none of which Compose
does at all.

### Q: How does the Horizontal Pod Autoscaler work here, and what are its actual limits?

**A:** Each business service has its own `HorizontalPodAutoscaler` (`k8s/base/13-hpa.yaml`): `minReplicas: 2`,
`maxReplicas: 5`, scaling on 70% average CPU utilization, with a 60-second stabilization window before scaling
back down (so a brief dip in load doesn't immediately shed capacity it might need again seconds later).
`discovery-server` and `config-server` are deliberately *not* scaled — they're intended as singletons in this
topology.

Worth being able to state plainly in an interview: **5 is a hard ceiling**, not an emergent property of the
system — if real traffic needs more than 5 replicas of a service, `maxReplicas` has to be raised explicitly
first; the system won't scale past it on its own no matter how much CPU pressure there is. The same is true in
the other direction for anything the HPA doesn't cover at all: Postgres is a single-replica `StatefulSet` with
no autoscaling story, which is a different, more structural scaling limit than anything HPA addresses (see the
capacity discussion this repo's own Q&A would point you back to — a single DB instance, not pod count, is
usually the real ceiling under sustained load).

### Q: What pod-level security hardening is actually configured, beyond "it runs in a container"?

**A:** Every business service's pod and container `securityContext` sets: `runAsNonRoot: true` with a fixed
non-root UID/GID (10001), `seccompProfile: RuntimeDefault` (restricts available syscalls to a vetted default
set), `allowPrivilegeEscalation: false`, `readOnlyRootFilesystem: true`, and `capabilities.drop: ["ALL"]`
(strips every Linux capability a container gets by default, rather than trusting none of them get misused).
None of this stops an application-level bug, but it meaningfully narrows what a *container escape* or a
compromised dependency could actually do on the host even if the application itself is exploited — the
container can't write to its own filesystem outside explicitly mounted volumes, can't escalate to root, and
starts with zero of the capabilities that most container breakouts rely on.

### Q: What's a NetworkPolicy, and what does "default-deny" actually mean in practice here?

**A:** By default, Kubernetes pods can reach any other pod in the cluster over the network — a NetworkPolicy
is how you restrict that. `k8s/hardening/network-policies.yaml` establishes a **default-deny baseline**:
`default-deny-ingress` and `default-deny-egress` apply to every pod (empty `podSelector`) and block all
traffic in both directions by default. Specific `allow-*` policies then punch narrow, explicit holes — e.g.
`allow-ingress-api-gateway` permits traffic *into* `api-gateway`, and a separate egress rule permits
`api-gateway` specifically to reach `discovery-server`, `config-server`, and Keycloak. The practical effect:
even if a pod is compromised, it can't just open a connection to an arbitrary other service in the cluster —
only the specific service-to-service paths this topology actually needs are allowed to exist at the network
layer at all, independent of and prior to any application-level auth check.

### Q: Is there a Kubernetes `Ingress` resource for external traffic?

**A:** No — worth knowing this is a deliberate choice this repo made, not a gap: external access runs through
an nginx reverse-proxy layer (`k8s/nginx-https/`) rather than a `kind: Ingress` object. Functionally similar
(TLS termination, routing into the cluster), but it's its own manifest/ConfigMap rather than relying on a
cluster's Ingress controller — worth noting as a difference if asked to compare this setup to a more typical
Ingress-based one.

### Q: ConfigMaps and Secrets both exist in Kubernetes for a reason — how does this repo split configuration
between them?

**A:** By sensitivity, applied consistently: non-sensitive values (Eureka URL, JWK-set URI, issuer, audience,
Kafka broker list, Redis host/port) are set as plain inline `env:` values directly in the Deployment manifest —
readable by anyone who can `kubectl describe` the pod, which is fine, since none of it is a credential.
Anything that *is* a credential (`EUREKA_PASSWORD`, `CONFIG_SERVER_PASSWORD`, `SPRING_DATA_REDIS_PASSWORD`) is
sourced via `secretKeyRef` against a `Secret` named `ecommerce-secrets` instead — a `Secret` at minimum gets
different RBAC treatment and isn't shown in plain `kubectl describe` output. (Interestingly, this repo's
business services don't actually use a `ConfigMap` object at all under `k8s/base` — the non-sensitive values
are inlined directly rather than externalized to a ConfigMap, since Spring Cloud Config Server already serves
that centralization role at the application layer; see [02](02-service-discovery-and-configuration.md).)
