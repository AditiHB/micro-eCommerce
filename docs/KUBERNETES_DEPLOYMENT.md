# Kubernetes Deployment Guide - Micro-eCommerce

A local-learning deployment of this project's microservices to Kubernetes
(Minikube or Kind), using [Kustomize](https://kustomize.io/) - built into
`kubectl` itself, no extra tool to install. Covers getting the whole stack
running, switching between an H2-per-pod profile and a real shared Postgres
profile (mirroring Docker Compose's own two modes), and genuine
HPA-based horizontal scaling - with the load test to actually trigger it.

This guide was rewritten alongside a full audit and fix of `k8s/` - see
["What was fixed"](#what-was-fixed) for the bug list, and
["What this round didn't cover"](#what-this-round-didnt-cover) for what was
deliberately left alone and why.

## Table of Contents

1. [Prerequisites](#prerequisites)
2. [Architecture Overview](#architecture-overview)
3. [Building the Images](#building-the-images)
4. [Deploying Locally](#deploying-locally)
5. [Verifying the Deployment](#verifying-the-deployment)
6. [Scaling](#scaling)
7. [Switching to Postgres](#switching-to-postgres)
8. [Troubleshooting](#troubleshooting)
9. [Cleanup](#cleanup)
10. [What was fixed](#what-was-fixed)
11. [What this round didn't cover](#what-this-round-didnt-cover)

## Prerequisites

- **Docker**: 20.10+
- **kubectl**: 1.25+ (this project's manifests assume at least this -
  `PodSecurityPolicy`, which the original manifests used, was removed in
  1.25; everything here is written against the current API surface)
- A local cluster - either:
  - **Minikube** 1.26+, or
  - **Kind** 0.17+
- **metrics-server** enabled in that cluster - required for the
  [Scaling](#scaling) section's HorizontalPodAutoscalers to report anything
  other than `<unknown>`. Setup is cluster-specific, covered below.

### Resource budget

Everything in `k8s/base` (minimum replica counts, before any HPA scale-up)
needs roughly **2.5 CPU / 3.5Gi memory** at requests, dominated by Kafka
(250m/512Mi) and two replicas each of API Gateway and the 5 business
services (250m/256Mi apiece). Give Minikube/Kind at least 4 CPU / 6Gi to
leave room for actual scale-up.

## Architecture Overview

- **Infrastructure**: Discovery Server (Eureka), Config Server, API Gateway,
  Kafka + Zookeeper (the saga's backbone), Redis (used only by the Gateway's
  rate limiter - see [its own note](#redis) below)
- **Business services**: Customer (8081), Order (8083), Inventory (8082),
  Payment (8084), Notification (8086) - all Kafka-driven via the
  choreographed saga, plus their own REST APIs behind the gateway
- **Data layer**: in-process H2 by default (one per pod - see
  [Switching to Postgres](#switching-to-postgres) for why that matters the
  moment you run more than one replica), or a real shared Postgres

```
                    ┌─────────────────────────┐
  outside traffic → │  api-gateway (Service:   │
                    │  LoadBalancer, 2 pods)   │
                    └───────────┬──────────────┘
                 Eureka-discovered lb:// routes
            ┌───────────┬───────┴───────┬───────────┐
       customer      order         inventory      payment
       -service      -service      -service        -service
       (8081)        (8083)        (8082)          (8084)
            │            │  Kafka choreography  │       │
            └──────┬─────┴───────────┬───────────┴───────┘
                   │                 │
            notification-service  kafka + zookeeper
               (8086, also
            calls customer/order
               directly)

  discovery-server (8761) + config-server (8888): every service above
  registers with/fetches from these two.
  redis (6379): only api-gateway's rate limiter actually uses this.
```

## Building the Images

Same Dockerfiles used by Docker Compose - nothing Kubernetes-specific here.

```bash
for service in discovery-server config-server api-gateway customer-service order-service inventory-service payment-service notification-service; do
    docker build -f Dockerfile.$service -t micro-ecommerce:$service .
done
```

### Minikube

```bash
eval $(minikube docker-env)
# re-run the build loop above - this builds straight into Minikube's own
# Docker daemon, so no separate load/push step is needed
```

### Kind

```bash
# build with your normal Docker daemon first (the loop above), then:
for service in discovery-server config-server api-gateway customer-service order-service inventory-service payment-service notification-service; do
    kind load docker-image micro-ecommerce:$service --name ecommerce
done
```

## Deploying Locally

### 1. Start the cluster with metrics-server enabled

**Minikube:**

```bash
minikube start --cpus 4 --memory 6144 --driver docker --kubernetes-version stable
minikube addons enable metrics-server
```

**Kind:**

```bash
kind create cluster --name ecommerce
# metrics-server needs one patch on Kind: its kubelet serving certs aren't
# signed by a CA metrics-server trusts by default.
kubectl apply -f https://github.com/kubernetes-sigs/metrics-server/releases/latest/download/components.yaml
kubectl patch deployment metrics-server -n kube-system --type=json \
  -p='[{"op":"add","path":"/spec/template/spec/containers/0/args/-","value":"--kubelet-insecure-tls"}]'
```

Confirm it's working before moving on (give it a minute after creation):

```bash
kubectl top nodes
```

### 2. Deploy the stack

Pick one profile - this is the same H2-vs-Postgres choice
`docker-compose.yml` vs. `docker-compose-postgres.yml` gives you locally:

```bash
# Default: H2, one database per pod. Good for a first run.
kubectl apply -k k8s/overlays/h2

# Or: real shared Postgres - needed once you run more than 1 replica per
# service and expect them to agree on the same data (see "Switching to
# Postgres" below).
kubectl apply -k k8s/overlays/postgres
```

### 3. Wait for everything to come up

```bash
kubectl get pods -n ecommerce -w
```

Kafka takes the longest to become ready (and api-gateway/the business
services will crash-loop a few times waiting on Eureka/Kafka before
settling - that's normal startup-ordering noise, not a bug, since nothing
here uses `initContainers` to block on every dependency).

### 4. Reach the API Gateway

```bash
# Minikube:
minikube service api-gateway -n ecommerce --url
# Kind (no LoadBalancer support by default) or either cluster, always works:
kubectl port-forward -n ecommerce svc/api-gateway 8080:80
curl http://localhost:8080/actuator/health
```

## Verifying the Deployment

```bash
kubectl get all -n ecommerce
kubectl get hpa -n ecommerce
kubectl logs -n ecommerce -l app=api-gateway --tail=100
kubectl describe pod -n ecommerce <pod-name>   # for anything not Ready
```

Confirm the saga is actually wired end to end (Kafka reachable, Eureka
registration working):

```bash
kubectl exec -it -n ecommerce deploy/discovery-server -- \
  curl -s http://localhost:8761/eureka/apps | grep -o '<name>[A-Z-]*</name>'
# Expect: API-GATEWAY, CUSTOMER-SERVICE, ORDER-SERVICE, INVENTORY-SERVICE,
# PAYMENT-SERVICE, NOTIFICATION-SERVICE, CONFIG-SERVER
```

If that list is short or empty, see [Troubleshooting](#troubleshooting).

## Scaling

### Manual

```bash
kubectl scale deployment -n ecommerce customer-service --replicas=4
```

Safe under `overlays/postgres` (shared database). Under `overlays/h2`,
each new replica starts with its own empty in-process database - reads can
land on a different pod than the write that created them. Fine for kicking
the tires with one replica; switch profiles before relying on more than
one.

### Automatic (HorizontalPodAutoscaler)

`k8s/base/13-hpa.yaml` defines a real HPA for the API Gateway and all 5
business services already - it's part of what `kubectl apply -k` just
deployed, not a separate step:

```bash
kubectl get hpa -n ecommerce
# NAME                   REFERENCE                         TARGETS   MINPODS   MAXPODS   REPLICAS
# customer-service       Deployment/customer-service        3%/70%    2         5         2
```

If TARGETS stays `<unknown>` instead of a percentage, metrics-server isn't
reachable - revisit step 1.

**Trigger a real scale-up** (no extra tooling needed - a handful of
parallel busy-loops from inside the cluster, hitting a real endpoint, is
enough against a 250m CPU request):

```bash
kubectl run load-generator -n ecommerce --image=busybox:1.36 --restart=Never -- \
  /bin/sh -c "for i in $(seq 1 20); do (while true; do wget -q -O- http://customer-service:8081/api/customers >/dev/null; done) & done; wait"
```

Watch it react, in another terminal:

```bash
kubectl get hpa -n ecommerce customer-service -w
```

Within a couple of minutes you should see TARGETS climb past 70% and
REPLICAS increase (up to the HPA's `maxReplicas: 5`). Stop the load once
you've seen it:

```bash
kubectl delete pod -n ecommerce load-generator
```

Then watch it **scale back down** - this is the "descaling" half of the
ask, and it's deliberately not instant:

```bash
kubectl get hpa -n ecommerce customer-service -w
```

The default HPA scale-down stabilization window is 5 minutes (this
project's HPAs shorten it to 60s via `behavior.scaleDown.stabilizationWindowSeconds`
- see `k8s/base/13-hpa.yaml` - specifically so this demo doesn't take
10+ minutes to show the other direction), so expect replicas to drop back
to `minReplicas: 2` within a minute or so of the load stopping, not
immediately.

## Switching to Postgres

Already deployed H2 and want to move to Postgres without starting over:

```bash
kubectl apply -k k8s/overlays/postgres
kubectl rollout restart deployment -n ecommerce customer-service order-service inventory-service payment-service notification-service
```

(`kubectl apply -k` alone changes the Deployments' env vars, but Kubernetes
only rolls pods when the pod template actually changes - which it does
here - so the restart above is usually redundant; included for certainty
if you've made other local edits.)

Each service creates its own schema via Flyway on first connection
(`db/migration/postgresql/*.sql` - see [db/README.md](../db/README.md)),
exactly like `docker-compose-postgres.yml` does.

To go back to H2: `kubectl apply -k k8s/overlays/h2` does **not** remove the
Postgres StatefulSet or revert the env var patch by itself (Kustomize only
adds/changes what its own resource list describes) - delete the Postgres
overlay's own resources first:

```bash
kubectl delete -k k8s/overlays/postgres
kubectl apply -k k8s/overlays/h2
```

## Troubleshooting

**Pods stuck in `CrashLoopBackOff` right after `kubectl apply`:** almost
always startup ordering (Kafka/Eureka not ready yet) - give it 2-3 minutes;
check `kubectl logs -n ecommerce <pod>` for the actual exception if it
doesn't resolve itself.

**Eureka's app list (see [Verifying](#verifying-the-deployment)) is
missing services:** check `EUREKA_INSTANCE_PREFER_IP_ADDRESS=true` is
present on the affected Deployment (`kubectl get deploy -n ecommerce
<name> -o yaml | grep -A1 PREFER_IP`) - without it, a Deployment's pods
register with Eureka under an unresolvable pod hostname instead of their
routable IP, since (unlike a StatefulSet) individual Deployment pods don't
get their own DNS record.

**`kubectl get hpa` TARGETS column stuck on `<unknown>`:** metrics-server
isn't reachable - `kubectl top pods -n ecommerce` will fail with the same
root cause; revisit [step 1](#1-start-the-cluster-with-metrics-server-enabled).

**Redis:** only `api-gateway`'s rate limiter actually talks to Redis in
this codebase - `customer-service`/`order-service`/etc.'s own caching
(`@Cacheable`) is backed by an in-process map regardless of what's
configured, a known, documented gap (see
`common/config/RedisConfig`'s own javadoc). `kubectl logs -n ecommerce -l
app=redis` / `kubectl exec -it -n ecommerce redis-0 -- redis-cli ping` work
the same as always if you need to debug the gateway's rate limiting
specifically.

**`kubectl apply -k k8s/hardening` makes everything unreachable:** check
your cluster's CNI actually enforces NetworkPolicy (Kind's default CNI
does; plain Minikube's does not unless started with `--cni=calico` or
similar) - if it doesn't enforce, the objects apply but do nothing, which
is a different, and much less alarming, non-issue.

## Cleanup

```bash
kubectl delete -k k8s/overlays/postgres   # or k8s/overlays/h2 - whichever you applied
kubectl delete -k k8s/hardening            # if you applied it
minikube delete      # or: kind delete cluster --name ecommerce
```

## What was fixed

A full audit of the original `k8s/*.yaml` + `helm/ecommerce/` found several
confirmed bugs, ranging from "silently breaks horizontal scaling" to
"takes the whole app down the moment you follow the doc's own cloud-deploy
instructions." Summary (full detail was in the audit itself, not
reproduced in full here):

- **No shared database across replicas** - every business service defaulted
  to an in-process H2 database with `replicas: 2`; two pods of the same
  service didn't agree on what data existed. Fixed by `k8s/overlays/postgres`.
- **A dead `h2-database` StatefulSet** - deployed, claimed a
  PersistentVolumeClaim, and nothing's JDBC URL ever pointed at it. Removed.
- **No HorizontalPodAutoscaler anywhere** - "scaling" was one imperative
  example command in this doc, never a committed resource. Fixed by
  `k8s/base/13-hpa.yaml`.
- **API Gateway had zero Redis configuration** - its rate limiter makes a
  blocking Redis call on every gated request; without `REDIS_HOST` it
  defaulted to `localhost`, breaking every such request in Kubernetes.
  Fixed.
- **Kafka/Zookeeper were entirely absent from `k8s/`** - the original
  manifests predate this project's Kafka-driven saga. Without them, no
  `@KafkaListener` in any service has anything to connect to. Added.
- **No path for Eureka registration to work in Kubernetes at all** - none
  of the original manifests set `EUREKA_INSTANCE_PREFER_IP_ADDRESS`, so
  every service would have registered under an unresolvable pod hostname
  instead of its routable IP; Docker Compose never surfaces this because
  container hostnames are resolvable there. Fixed on every service.
- **`order-service` and `inventory-service` had their ports swapped**
  relative to Docker Compose and their own `application.yml` defaults
  (consistently enough internally that it wouldn't have crashed, just run
  on the wrong port vs. every other deployment path for the same service -
  and `notification-service`'s `ORDER_SERVICE_URL` pointed at the resulting
  wrong port too). Fixed.
- **A config-drift trap**: every service mounted a ConfigMap as
  `/app/config/application.yml`, duplicating config already baked into the
  image and in one case actively conflicting with it (`hibernate.ddl-auto:
  update` fighting Flyway's own schema versioning, which the image's real
  config sets to `validate`). Removed in favor of plain env vars, matching
  how `docker-compose.yml` already does it.
- **A Redis password mismatch** - a secret defined one, 4 services sent it,
  and the actual Redis container never enforced one. Resolved by matching
  Docker Compose's own fidelity: no password, since nothing here currently
  needs one.
- **The NetworkPolicy file would have taken the whole app down if applied**
  (and `kubectl apply -f k8s/`, previously documented here, applied it along
  with everything else) - see `k8s/hardening/network-policies.yaml`'s own
  header for the specifics. Rewritten from the project's real dependency
  graph, not patched.
- **The RBAC file used a `PodSecurityPolicy`**, an API kind removed in
  Kubernetes 1.25 (this project's own stated minimum version), and an
  `audit.k8s.io/v1 Policy` object, which isn't `kubectl apply`-able at all.
  Replaced with a working per-service ServiceAccount+Role+RoleBinding set
  in `k8s/base/02-rbac.yaml`.

## What this round didn't cover

Documented rather than silently dropped, matching this project's existing
"known gaps" convention (see `e2e-tests/README.md`):

- **mTLS + cert-manager + the split `ecommerce-secrets` namespace.** The
  original attempt here had several independent, fatal problems: Certificates
  issued for the wrong namespace (`default` instead of `ecommerce`), a
  keystore path pointing at a PEM `.crt` file where Spring Boot needs an
  actual PKCS12 keystore (nothing converted one to the other), a second
  Secret namespace that Kubernetes architecturally cannot use for
  `secretKeyRef` env injection across namespaces regardless of RBAC, and a
  file using `kind: Patch` - not a real Kubernetes API kind. A correct
  version of this is a substantial project on its own (real keystore
  generation via an init container, at minimum) and was cut rather than
  rebuilt under this pass's local-learning scope.
- **`k8s/nginx-https`** - kept, not verified working end-to-end in this
  pass. See its own README.
- **Helm chart parity** - `helm/ecommerce` was not brought to the same fix
  bar as `k8s/`. See `helm/ecommerce/NOTE.md`.
- **Cloud deployment (EKS/GKE/ACR, Let's Encrypt).** This round's scope was
  explicitly local learning (Minikube/Kind); none of the cloud-specific
  assumptions from the previous version of this guide were re-verified, and
  the Let's Encrypt `ClusterIssuer`s that used to live in the (now removed)
  cert-manager setup need a publicly resolvable domain for their HTTP01
  challenge anyway - inapplicable to a local cluster regardless.
