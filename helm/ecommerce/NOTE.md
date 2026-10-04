# This chart is not the maintained local-deployment path

As of the Kubernetes rework documented in `docs/KUBERNETES_DEPLOYMENT.md`,
`k8s/` (Kustomize base + overlays) is the maintained, bug-fixed way to
deploy this project to a local cluster. This Helm chart was audited
alongside it and found to share most of the same bugs (no Redis wiring for
the API Gateway's rate limiter, no Kafka/Zookeeper at all, the swapped
order-service/inventory-service ports, an orphaned `h2Database` StatefulSet
enabled by default) plus its own gap on top: it has no `notification-service`
template at all, despite that being a real, running part of the Kafka saga.

**Decision (documented per this project's own "known gaps" convention,
rather than silently left to rot): this chart is kept for reference but not
brought to the same fix bar as `k8s/`.** Reasoning: bringing it to parity
means re-implementing every fix twice - Kafka/Zookeeper templates, the
Postgres profile toggle, the HPA templates, the RBAC/NetworkPolicy
equivalents - in Helm's templating language as well as in Kustomize, which
roughly doubles the surface area of this fix for a tool that isn't needed to
satisfy "deploy locally for learning" (Kustomize ships inside `kubectl`
itself; Helm is an extra tool to install and a templating layer on top of
the same YAML). If you want Helm specifically - e.g. to practice it as a
tool - treat this chart as a known-stale starting point, not a working
deployment, and expect to port the fixes from `k8s/base` and
`k8s/overlays/postgres` into it by hand.
