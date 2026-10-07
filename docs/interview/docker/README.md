# Docker Interview Prep

Interview-style question-and-answer guide to how this repository actually uses Docker — multi-stage builds,
Compose orchestration with health-gated startup ordering, and runtime hardening — grounded in this repo's own
`Dockerfile.*` files and `docker-compose.yml`. This is the container-build-and-local-orchestration layer
underneath [`../kubernetes/`](../kubernetes/README.md), which covers the same images once they're actually
scheduled onto a cluster.

| # | Topic | What it covers |
|---|-------|----------------|
| [01](01-dockerfiles-and-multi-stage-builds.md) | Dockerfiles & multi-stage builds | The build/runtime stage split, layer-cache-friendly `COPY` ordering, the non-root runtime user |
| [02](02-compose-orchestration-dependencies-and-profiles.md) | Compose orchestration, dependencies & profiles | `depends_on` + `condition: service_healthy`, opt-in service groups via `profiles:`, the config-server self-init entrypoint |
| [03](03-image-and-runtime-practices.md) | Image & runtime practices | `HEALTHCHECK`, resource constraints, why `curl` is the one extra package installed, image size tradeoffs |

## How to use this with the repo

Open any `Dockerfile.*` at the repo root alongside this guide — they're nearly identical in shape by design
(one shared pattern, repeated per service), so reading one closely is close to reading all of them.
