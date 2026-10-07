# Networking & Zero Trust

### Q: By default, any pod in a Kubernetes cluster can reach any other pod — how does this repo restrict
that, and what does "default-deny" actually mean in practice?

**A:** `k8s/hardening/network-policies.yaml` establishes two baseline policies applied to **every** pod (an
empty `podSelector: {}` matches the whole namespace): `default-deny-ingress` and `default-deny-egress`, each
blocking all traffic in that direction by default, for every pod, with no exceptions baked in. On top of that
baseline, specific narrow `allow-*` policies punch exactly the holes this topology actually needs — e.g.
`allow-ingress-api-gateway` permits traffic *into* `api-gateway` from wherever it needs to accept it, and a
paired egress rule permits `api-gateway` specifically to reach `discovery-server`, `config-server`, and
Keycloak, and nothing else. The practical effect: even a fully compromised pod can't simply open a connection
to an arbitrary other service in the cluster to pivot further — only the specific service-to-service paths this
system's own topology genuinely requires are allowed to exist at the network layer **at all**, independent of,
and prior to, any application-level authentication or authorization check on top of it.

### Q: Why does this matter *in addition to* the mTLS and JWT layers already covered in the microservices
guide — isn't this overlap?

**A:** Each layer closes a gap the others structurally can't, which is the actual argument for defense in
depth rather than redundancy for its own sake (see
[../microservices/10](../microservices/10-security.md) for the full layering argument). `NetworkPolicy` is the
only one of the three that operates **before any packet is even allowed to be sent** — mTLS and JWT both assume
a connection has already been permitted to open, and then authenticate/authorize *within* it. A
misconfiguration that somehow let a rogue or compromised pod attempt a connection to, say, a database it has no
business reaching would still need to get past mTLS (no valid client certificate) and then past JWT validation
(no valid token for that caller) if either of those happened to apply to that particular connection at all —
but `NetworkPolicy` is what stops the connection attempt from succeeding in the first place, regardless of
whether a later layer would have caught it too. It's the layer that fails safe even if a later one has a bug.

### Q: There's no Kubernetes `Ingress` resource anywhere in this repo — how does external traffic actually
reach the cluster, and is that a gap?

**A:** Worth knowing this is a deliberate choice, not an oversight: external access runs through an nginx
reverse-proxy layer (`k8s/nginx-https/`, with its own `ConfigMap`) rather than a `kind: Ingress` object backed
by a cluster's Ingress controller. Functionally, it does a similar job — TLS termination and routing traffic
into the cluster — but as its own manifest/ConfigMap this project manages directly, rather than relying on
whatever Ingress controller a given cluster happens to have installed (nginx-ingress, a cloud provider's own
controller, etc.), each with its own slightly different annotation conventions and feature set. It's a
reasonable choice for a project meant to be deployable and demonstrable without first requiring the operator to
have a specific Ingress controller already set up — the tradeoff is giving up the portability and
feature-richness (automatic cert provisioning via cert-manager annotations, path-rewrite rules, etc.) a
standard `Ingress` resource would get you more easily on a cluster that already has one configured.

### Q: If you added a brand-new service to this system tomorrow, what would you actually need to do at the
networking layer for it to work at all, given this default-deny baseline?

**A:** Beyond the obvious application-level wiring, you'd need an explicit `allow-*` `NetworkPolicy` entry for
every direction of traffic that new service genuinely needs — an ingress rule from whatever's allowed to call
it (likely `api-gateway`, by `podSelector` matching its label), and an egress rule for whatever it needs to
reach outbound (at minimum `discovery-server` and `config-server`, following this repo's existing pattern, plus
anything else specific to that service — a database, Kafka, another service it calls synchronously). Forgetting
this step wouldn't show up as an obvious error at deploy time — the pod would start, pass its health checks
(which only need the management port, often reachable regardless), and only fail the moment it actually tried
to make a network call this policy set hadn't anticipated, surfacing as a connection timeout deep in that
specific request path rather than an upfront, obvious configuration error. That gap between "deploys cleanly"
and "actually works end to end" is exactly why a default-deny network model needs networking changes treated as
a first-class part of adding any new service, not an afterthought discovered via a failing request later.
