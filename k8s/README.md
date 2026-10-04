# Kubernetes manifests

Structured as a Kustomize base + overlays, so the same choice Docker Compose
gives you (H2 vs. a real shared Postgres) exists here too. Full walkthrough,
HPA-based scaling demo, and a list of what was fixed vs. deliberately cut is
in [docs/KUBERNETES_DEPLOYMENT.md](../docs/KUBERNETES_DEPLOYMENT.md) - this
file is just the map.

```
k8s/
  base/               Every core resource: namespace, RBAC, Kafka/Zookeeper,
                       Redis, discovery/config-server, API Gateway, the 5
                       business services, HPAs. Defaults to H2 (no
                       datasource env vars set - each service's own image
                       falls back to its built-in in-process H2 default).
  overlays/
    h2/                kubectl apply -k k8s/overlays/h2
                        The default profile - just k8s/base, unpatched.
    postgres/           kubectl apply -k k8s/overlays/postgres
                        k8s/base plus a real, shared Postgres instance (same
                        image/credentials/init-script as
                        docker-compose-postgres.yml) and a patch switching
                        the 5 business services onto it. Use this one for
                        anything involving more than 1 replica per service -
                        H2 is private per-pod, so multiple replicas of the
                        same service silently disagree about what data
                        exists under overlays/h2.
  hardening/           Optional zero-trust NetworkPolicy set. Not applied by
                       either overlay above - apply on top, once the base
                       stack already works: kubectl apply -k k8s/hardening
  nginx-https/         Optional TLS-terminating reverse proxy, mirrors
                       docker-compose.yml's https profile. Not brought to the
                       same verified bar as the rest in this pass - see its
                       own README.
```

No more flat, numbered `k8s/*.yaml` files applied with `kubectl apply -f
k8s/` - that pattern used to silently sweep in several broken, half-finished
manifests (cert-manager/mTLS, a since-removed Kubernetes API kind, a
NetworkPolicy that would have cut the app off from itself) alongside the
ones that actually worked. Every resource now lives under a folder that says
what applying it does and when to use it.
