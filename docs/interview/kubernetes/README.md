# Kubernetes Interview Prep

Interview-style question-and-answer guide to how this repository is actually deployed on Kubernetes —
workloads, autoscaling, health-driven self-healing, configuration/secrets, zero-trust networking, and
Kustomize's base-plus-overlays structure — grounded in `k8s/base/*.yaml`, `k8s/overlays/*`, and
`k8s/hardening/*`. This is the cluster-scheduling layer on top of [`../docker/`](../docker/README.md), which
covers how the images being scheduled here are actually built.

| # | Topic | What it covers |
|---|-------|----------------|
| [01](01-workloads-and-deployments.md) | Workloads & Deployments | Deployment vs. StatefulSet, replica counts, Service discovery alongside Eureka |
| [02](02-scaling-and-resource-management.md) | Scaling & resource management | The HPA's actual ceiling, `requests`/`limits`, how pod sizing ties to this repo's own capacity analysis |
| [03](03-health-probes-and-self-healing.md) | Health probes & self-healing | Liveness vs. readiness in practice, the `readOnlyRootFilesystem` + `emptyDir` `/tmp` pattern |
| [04](04-configuration-secrets-and-rbac.md) | Configuration, Secrets & RBAC | The ConfigMap/Secret split, Kustomize generators, least-privilege service accounts (and a bundled manifest fixed on arrival) |
| [05](05-networking-and-zero-trust.md) | Networking & zero trust | Default-deny `NetworkPolicy`, why there's no `Ingress` object here, pod-to-pod traffic control |
| [06](06-statefulsets-kustomize-and-overlays.md) | StatefulSets, Kustomize & overlays | Postgres's `volumeClaimTemplates`, the base+overlay structure, and a stale overlay comment caught by actually checking |

## How to use this with the repo

As with every guide in this series: when an answer cites a manifest and line, open it — several of these
answers were only reached by directly grepping the actual YAML rather than trusting a comment or an assumption,
which is exactly the habit worth modeling while studying this material.
