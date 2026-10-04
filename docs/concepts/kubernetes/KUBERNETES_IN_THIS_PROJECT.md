# Kubernetes in the Micro-eCommerce Project 🏪

Rewritten to match what's actually deployed in `k8s/` - the previous version
of this doc described a generic Kubernetes tutorial with this project's name
pasted on top (a `Delivery Service` that doesn't exist, replica/memory
numbers matching no real manifest, a NodePort example and an Ingress
controller never deployed here). Every number and example below is read
straight from `k8s/base` - if a manifest changes, this doc can drift again,
so when in doubt, `kubectl get` is more authoritative than this file.

## The Big Picture

```
┌──────────────────────────────────────────────────────┐
│  🚪 API Gateway            (2 pods, scales to 5)      │
│  👤 Customer Service        (2 pods, scales to 5)      │
│  📦 Order Service           (2 pods, scales to 5)      │
│  📊 Inventory Service       (2 pods, scales to 5)      │
│  💳 Payment Service         (2 pods, scales to 5)      │
│  📧 Notification Service    (2 pods, scales to 5)      │
│  🧭 Discovery Server (Eureka)   (1 pod, not scaled)     │
│  ⚙️  Config Server               (1 pod, not scaled)     │
│  📨 Kafka + Zookeeper            (1 pod each)           │
│  💾 Redis                        (1 pod)                │
└──────────────────────────────────────────────────────┘
```

No Delivery Service - that's not a real part of this project. The saga is
choreographed over Kafka between Order/Inventory/Payment/Notification; see
`docs/SAGA_PATTERN_GUIDE.md` for the actual flow.

## A Real Deployment From This Project

**Declaration** (`k8s/base/08-customer-service.yaml`, trimmed):
```yaml
Deployment: customer-service
├─ replicas: 2
├─ image: micro-ecommerce:customer-service
├─ requests: 250m CPU / 256Mi memory
├─ limits: 500m CPU / 512Mi memory
└─ port: 8081
```

**What Kubernetes does with it:**
```
✅ Creates 2 customer-service pods
✅ Watches both continuously
✅ If one dies → replaces it automatically
✅ If CPU averages over 70% across both → the HPA in
   k8s/base/13-hpa.yaml asks for more (up to 5)
✅ Routes requests to whichever pod is Ready
```

## How a Request Actually Gets Routed Here

```
Customer in browser / curl
       ↓
Service: api-gateway (type: LoadBalancer, k8s/base/07-api-gateway.yaml)
       ↓
One of the 2 (or more, under load) api-gateway pods
       ↓
Gateway's own baked-in route: uri: lb://customer-service
       ↓ (resolved via Eureka, NOT a hardcoded host:port)
Eureka (discovery-server) returns the real pod IPs currently registered
       ↓
Spring Cloud LoadBalancer picks one
       ↓
That customer-service pod handles the request
```

The `lb://` scheme matters: this is why every service sets
`EUREKA_INSTANCE_PREFER_IP_ADDRESS=true`. A Deployment's pods (unlike a
StatefulSet's) don't get individually resolvable DNS names - only the
Service does - so without that setting, Eureka would register each pod
under an unresolvable hostname and this whole chain would break the moment
there was more than one pod to choose from.

## Pods and Deployments, With Real Numbers

```
Deployment: customer-service
Desired: 2 pods   Current: 2 pods
├─ customer-service-7d9f8b6c99-a1b2c: Running ✅
└─ customer-service-7d9f8b6c99-x3y4z: Running ✅

If a1b2c crashes:
   ↓
Deployment: "Should be 2, only 1 running!"
   ↓
Creates customer-service-7d9f8b6c99-q5r6s
   ↓
Back to 2 running
```

## Services (Stable Addresses), as Actually Used Here

#### ClusterIP - everything internal

```
order-service's own Service (k8s/base/09-order-service.yaml):
  type: ClusterIP, port 8083

Any pod in the ecommerce namespace reaches it the same way, always:
  http://order-service:8083
regardless of which specific pod currently answers.
```

#### LoadBalancer - the one externally-facing entry point

```
api-gateway's Service: type: LoadBalancer, port 80 → containerPort 8080

Minikube:  minikube service api-gateway -n ecommerce --url
Kind (no real LoadBalancer support locally): kubectl port-forward
  -n ecommerce svc/api-gateway 8080:80
```

Nothing else in this project uses NodePort or has its own Ingress resource
- `k8s/nginx-https` is an *optional*, not-yet-verified alternative front
door (see its own README), not something deployed by default.

## Auto-Scaling: The Actual HPA, Not a Generic Example

```yaml
# k8s/base/13-hpa.yaml, for customer-service
minReplicas: 2
maxReplicas: 5
metrics:
  - type: Resource
    resource:
      name: cpu
      target: { type: Utilization, averageUtilization: 70 }
behavior:
  scaleDown: { stabilizationWindowSeconds: 60 }
```

```
kubectl top reports CPU usage from metrics-server
       ↓
Averaged across customer-service's pods: say 85%
       ↓
HPA controller: "Above the 70% target, and 2 < maxReplicas (5)"
       ↓
Scale to 3 (then checks again ~15s later, scales further if still hot)
       ↓
Load drops back under 70%
       ↓
HPA waits out its 60s scale-down window, then scales back toward 2
```

This genuinely works, with a load test you can run yourself -
`docs/KUBERNETES_DEPLOYMENT.md`'s ["Scaling"](../../KUBERNETES_DEPLOYMENT.md#scaling)
section has the exact command. `discovery-server` and `config-server` are
deliberately *not* autoscaled - this project only ever runs one replica of
each by design (Eureka's own self-preservation model and a git-backed config
server don't behave like a stateless HTTP tier under naive CPU-based HPA).

## Persistent Data: Two Real Profiles, Not a Generic PV Example

```
Profile 1 - k8s/overlays/h2 (the default):
   Each service pod → its own in-process H2 database
   ↓
   Simple, zero extra infrastructure, but NOT shared between replicas -
   a write that lands on pod A is invisible from pod B.

Profile 2 - k8s/overlays/postgres:
   Each service pod → the same shared `postgres` StatefulSet
   (1 PersistentVolumeClaim, 2Gi, survives pod restarts)
   ↓
   What makes running more than 1 replica per service actually safe.
```

This is this project's real "why you'd want a PersistentVolumeClaim"
story - not a generic example database.

## ConfigMaps and Secrets, as Actually Used Here

```
Secret: ecommerce-secrets (k8s/base/01-secrets.yaml)
└─ jwt-secret - the only thing in it. (Not db-username/password or a
   redis-password - this project's H2/Postgres credentials and its
   currently-unauthenticated local Redis don't need secret-sourced values;
   see k8s/base/04-redis.yaml's own header for why.)

customer-service's Deployment:
   ↓
env: JWT_SECRET valueFrom secretKeyRef(ecommerce-secrets, jwt-secret)
   ↓
Available to the app as the JWT_SECRET env var, same as docker-compose.yml
```

No per-service ConfigMap holding a mounted `application.yml` - the original
version of these manifests did that, and it silently drifted from the
image's own baked-in config (in one case fighting Flyway's own schema
versioning). Every override here is a plain env var instead, matching how
`docker-compose.yml` already does it.

## Health Checks, With Real Endpoints

```
customer-service pod just started:
       ↓
Kubernetes checks every 5s (periodSeconds) via Spring Boot Actuator:
       GET http://pod-ip:8081/actuator/health/readiness
       ↓
200 OK → Ready, traffic starts flowing
Non-200 → wait, try again (up to failureThreshold: 3)

Separately, every 15s:
       GET http://pod-ip:8081/actuator/health/liveness
       ↓
Non-200 across 3 tries → Kubernetes kills and restarts the pod
```

Real endpoints, real intervals, straight from
`k8s/base/08-customer-service.yaml` - not a generic `/health/live` example.

## RBAC: Real, and Mostly for Learning Value

```
ServiceAccount: customer-service-sa
       ↓
Role: read-ecommerce-secrets (get, on Secret "ecommerce-secrets" only)
       ↓
RoleBinding ties them together
```

Worth being honest about: none of these services actually call the
Kubernetes API themselves (no client library in use anywhere in this
codebase), so this Role doesn't change what the app can do -
`secretKeyRef` env injection is resolved by the kubelet, not the pod's own
RBAC permissions. It's here as a genuine, working least-privilege example
and as the hook point if a service ever does need API access later - not
because this app needs it today.

## Commands That Actually Apply to This Project

```bash
# See everything in this project's namespace
kubectl get all -n ecommerce

# Deploy (pick one - see docs/KUBERNETES_DEPLOYMENT.md)
kubectl apply -k k8s/overlays/h2
kubectl apply -k k8s/overlays/postgres

# Scale manually
kubectl scale deployment -n ecommerce customer-service --replicas=4

# Watch the real HPA react
kubectl get hpa -n ecommerce -w

# Logs from a real deployment here
kubectl logs -n ecommerce -l app=customer-service --tail=100

# Eureka's actual registered services
kubectl exec -it -n ecommerce deploy/discovery-server -- \
  curl -s http://localhost:8761/eureka/apps
```

---

*For the genuinely generic Kubernetes concepts (what's a Pod, what's a
Namespace, etc.) see [KUBERNETES_FOR_BEGINNERS.md](KUBERNETES_FOR_BEGINNERS.md)
- that one isn't making project-specific claims, so it doesn't need the same
grounding this file does. For the full deployment walkthrough, HPA load
test, and the list of what was fixed vs. deliberately cut in this round, see
[docs/KUBERNETES_DEPLOYMENT.md](../../KUBERNETES_DEPLOYMENT.md).*
