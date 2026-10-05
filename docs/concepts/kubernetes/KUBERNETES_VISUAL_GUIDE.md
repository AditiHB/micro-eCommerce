# Kubernetes Visual Guide 🎨

## The Basic Concept - Container Orchestration

```
WITHOUT KUBERNETES (Manual Management)
──────────────────────────────────────

    🧑 DevOps Engineer
    |
    ├─ Server 1: Order Service (stuck here)
    ├─ Server 2: Payment Service (stuck here)
    ├─ Server 3: Inventory Service (stuck here)
    
    Problem: One server down = manual fix needed
    Problem: Traffic spike = manual scaling
    Problem: New service = buy new server


WITH KUBERNETES (Automated)
──────────────────────────────

    🏢 Kubernetes Cluster
    |
    ├─ All services deployed
    ├─ Auto-healing
    ├─ Auto-scaling
    ├─ Load balancing
    ├─ Zero-downtime updates
    
    Benefit: Services move around automatically
    Benefit: Scales up/down by itself
    Benefit: One service down? Auto-replaced!
```

---

## Cluster Architecture

```
KUBERNETES CLUSTER
═════════════════════════════════════════════════════════════

┌─────────────────────────────────────────────────────────────┐
│ CONTROL PLANE (Master)                                      │
│ ┌───────────────┐  ┌───────────────┐  ┌───────────────┐   │
│ │ API Server    │  │ Scheduler     │  │ Controller    │   │
│ │ (Brain)       │  │ (Placer)      │  │ Manager       │   │
│ │               │  │               │  │ (Monitor)     │   │
│ └───────────────┘  └───────────────┘  └───────────────┘   │
│                                                             │
│ etcd (Database) - stores all configuration                │
└─────────────────────────────────────────────────────────────┘
                            ↑
        ┌───────────────────┼───────────────────┐
        ↓                   ↓                   ↓

┌──────────────────┐  ┌──────────────────┐  ┌──────────────────┐
│ NODE 1           │  │ NODE 2           │  │ NODE 3           │
│ (Worker)         │  │ (Worker)         │  │ (Worker)         │
│                  │  │                  │  │                  │
│ ┌──────────────┐ │  │ ┌──────────────┐ │  │ ┌──────────────┐ │
│ │ kubelet      │ │  │ │ kubelet      │ │  │ │ kubelet      │ │
│ │ (Manager)    │ │  │ │ (Manager)    │ │  │ │ (Manager)    │ │
│ └──────────────┘ │  │ └──────────────┘ │  │ └──────────────┘ │
│ ┌──────────────┐ │  │ ┌──────────────┐ │  │ ┌──────────────┐ │
│ │ 📦 Pod 1     │ │  │ │ 📦 Pod 3     │ │  │ │ 📦 Pod 5     │ │
│ │ Order Svc    │ │  │ │ Payment Svc  │ │  │ │ Inventory    │ │
│ └──────────────┘ │  │ └──────────────┘ │  │ └──────────────┘ │
│ ┌──────────────┐ │  │ ┌──────────────┐ │  │ ┌──────────────┐ │
│ │ 📦 Pod 2     │ │  │ │ 📦 Pod 4     │ │  │ │ 📦 Pod 6     │ │
│ │ Order Svc    │ │  │ │ Payment Svc  │ │  │ │ Notification │ │
│ └──────────────┘ │  │ └──────────────┘ │  │ └──────────────┘ │
│                  │  │                  │  │                  │
│ Resources:       │  │ Resources:       │  │ Resources:       │
│ CPU: 4 cores     │  │ CPU: 8 cores     │  │ CPU: 2 cores     │
│ RAM: 8GB         │  │ RAM: 16GB        │  │ RAM: 4GB         │
└──────────────────┘  └──────────────────┘  └──────────────────┘
```

---

## Pod and Container Hierarchy

```
KUBERNETES HIERARCHY
═════════════════════════════════════════════════════════════

Cluster
  ↓
┌─────────────────────────────────────────────────────────────┐
│ Namespace: production                                       │
│                                                             │
│  Deployment: order-service                                 │
│  (Keep 3 running)                                           │
│  ├─ ReplicaSet: order-service-5f8d9c                       │
│  │  ├─ Pod: order-service-5f8d9c-abc1                      │
│  │  │  └─ 🐳 Container: order-service:v1.0               │
│  │  │                                                      │
│  │  ├─ Pod: order-service-5f8d9c-def2                      │
│  │  │  └─ 🐳 Container: order-service:v1.0               │
│  │  │                                                      │
│  │  └─ Pod: order-service-5f8d9c-ghi3                      │
│  │     └─ 🐳 Container: order-service:v1.0               │
│  │                                                          │
│  Service: order-service                                    │
│  (Stable address: order-service:8080)                      │
│  Selector: app=order-service                               │
│  Routes to: Any pod with label app=order-service           │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

---

## Deployment Process

```
CREATING AND UPDATING SERVICES
═════════════════════════════════════════════════════════════

STEP 1: DEPLOYMENT
────────────────
Developer writes deployment.yaml:
  apiVersion: apps/v1
  kind: Deployment
  metadata:
    name: order-service
  spec:
    replicas: 2
    image: micro-ecommerce:order-service
        ↓
kubectl apply -k k8s/overlays/h2
        ↓
Kubernetes reads and creates:
  └─ Deployment ✅
  └─ ReplicaSet ✅
  └─ 2 Pods ✅


STEP 2: MONITORING
────────────────
Kubernetes continuously checks:
  Pod 1: Running ✅
  Pod 2: Running ✅
  Pod 3: Running ✅
        ↓
Pod 2 crashes 💥
        ↓
Kubernetes: "Should be 3, only 2!"
        ↓
Creates Pod 4
        ↓
  Pod 1: Running ✅
  Pod 3: Running ✅
  Pod 4: Running ✅ (NEW)


STEP 3: ROLLING UPDATE
────────────────────
Update to v2.0:
  kubectl set image deployment/order-service \
    order-service=order-service:v2.0

Timeline:
  Time 0:   [Pod1 v1.0] [Pod2 v1.0] [Pod3 v1.0]
  Time 1:   [Pod1 v2.0] [Pod2 v1.0] [Pod3 v1.0]
  Time 2:   [Pod1 v2.0] [Pod2 v2.0] [Pod3 v1.0]
  Time 3:   [Pod1 v2.0] [Pod2 v2.0] [Pod3 v2.0]
           ✅ Zero downtime!
```

---

## Service Discovery and Load Balancing

```
SERVICE ROUTING
═════════════════════════════════════════════════════════════

REQUEST FLOW
────────────

Client Pod (Order Service):
  ↓
  Makes request to: http://payment-service:8080
  ↓
Kubernetes DNS resolves: payment-service → 10.0.0.100
  ↓
Request goes to Service IP: 10.0.0.100:8080
  ↓
Service has multiple backend pods:
  ┌──────────────────────────────────┐
  │ Service: payment-service         │
  │ IP: 10.0.0.100                   │
  │                                  │
  │ Endpoints (backend pods):        │
  │ ├─ 10.1.0.5:8080 (Pod 1)        │
  │ ├─ 10.1.1.8:8080 (Pod 2)        │
  │ └─ 10.1.2.3:8080 (Pod 3)        │
  └──────────────────────────────────┘
  ↓
Service load-balances (round-robin):
  Request 1 → Pod 1
  Request 2 → Pod 2
  Request 3 → Pod 3
  Request 4 → Pod 1
  (And so on...)
  ↓
Response back to Order Service
  ↓
✅ Request complete!


WHAT HAPPENS WHEN POD DIES
──────────────────────────
Pod 2 dies 💥
  ↓
Kubernetes removes it from Service endpoints:
  ┌──────────────────────────────────┐
  │ Service: payment-service         │
  │ IP: 10.0.0.100                   │
  │                                  │
  │ Endpoints (backend pods):        │
  │ ├─ 10.1.0.5:8080 (Pod 1) ✅    │
  │ └─ 10.1.2.3:8080 (Pod 3) ✅    │
  │                                  │
  │ (Pod 2 gone, no requests go there)
  └──────────────────────────────────┘
  ↓
Kubernetes creates new Pod 4
  ↓
Adds Pod 4 to Service endpoints
  ↓
Back to 3 pods, zero impact on traffic! ✅
```

---

## Auto-Scaling

```
HORIZONTAL POD AUTOSCALING (HPA)
═════════════════════════════════════════════════════════════

NORMAL TRAFFIC
──────────────
Min: 2 pods  |  Current: 2 pods  |  Max: 10 pods

Order Service:
  ├─ Pod 1: 30% CPU ✅
  └─ Pod 2: 35% CPU ✅
  
Status: All good, no scaling


TRAFFIC SPIKE
──────────────
Min: 2 pods  |  Current: 5 pods  |  Max: 10 pods

Metrics:
  CPU: 85% 🔴
  Memory: 80% 🟠
  
Autoscaler: "High CPU! Need more pods!"
  ↓
Create: Pod 3, Pod 4, Pod 5
  ↓
Now: 5 pods
  ↓
CPU drops to 60% ✅


TRAFFIC DIES DOWN
──────────────────
Min: 2 pods  |  Current: 2 pods  |  Max: 10 pods

Metrics:
  CPU: 20% ✅
  Memory: 15% ✅
  
Autoscaler: "Low usage! Reduce pods!"
  ↓
Remove: Pod 3, Pod 4, Pod 5
  ↓
Now: 2 pods
  ↓
Save resources! 💰
```

---

## Persistent Storage

```
VOLUMES AND STORAGE
═════════════════════════════════════════════════════════════

TEMPORARY STORAGE (emptyDir)
──────────────────────────────
Pod created:
  ├─ emptyDir volume created
  ├─ Pod writes files to /tmp/cache
  └─ Container runs

Pod deleted:
  └─ emptyDir deleted too ❌
  └─ Data lost


PERSISTENT STORAGE (PersistentVolume)
──────────────────────────────────────
Pod created:
  ├─ Requests PersistentVolumeClaim
  ├─ Gets 10GB of persistent storage
  ├─ Mounts to /data
  ├─ Writes database files
  └─ Container runs

Pod deleted:
  └─ Storage remains! ✅
  └─ Data safe!

Pod recreated (maybe on different node):
  ├─ Claims same PersistentVolume
  ├─ Mounts to /data
  ├─ Data still there! ✅
  └─ All good!
```

---

## Health Checks

```
LIVENESS AND READINESS PROBES
═════════════════════════════════════════════════════════════

STARTUP
───────
Pod starts
  ↓
│ Is service ready? (Readiness check)
├─ /health/ready → 500 (Not ready)
├─ Wait...
├─ /health/ready → 500 (Not ready)
├─ Wait...
├─ /health/ready → 200 (Ready!) ✅
│
└─ Service ready, send traffic!


RUNNING
───────
Service running
  ↓
│ Every 10 seconds (Liveness check)
├─ /health/live → 200 (Alive!) ✅
├─ /health/live → 200 (Alive!) ✅
├─ /health/live → 200 (Alive!) ✅
├─ Service is working
│
└─ Continue normally


CRASH
───────
Service crashes
  ↓
│ Liveness check
├─ /health/live → No response ❌
├─ Timeout! ❌
│
└─ Kubernetes: "Pod died! Restarting..."
   ├─ Kill pod
   ├─ Create new pod
   └─ Back online in seconds ✅
```

---

## Namespaces

```
NAMESPACES - VIRTUAL CLUSTERS
═════════════════════════════════════════════════════════════

SINGLE PHYSICAL CLUSTER
───────────────────────

┌─────────────────────────────────────────────────┐
│ Kubernetes Cluster (1000 nodes)                 │
│                                                 │
│ ┌────────────────┐ ┌────────────────┐          │
│ │ default        │ │ kube-system    │          │
│ │ namespace      │ │ namespace      │          │
│ └────────────────┘ └────────────────┘          │
│                                                 │
│ ┌────────────────┐ ┌────────────────┐          │
│ │ production     │ │ staging        │          │
│ │ namespace      │ │ namespace      │          │
│ └────────────────┘ └────────────────┘          │
│                                                 │
│ ┌────────────────┐                              │
│ │ development    │                              │
│ │ namespace      │                              │
│ └────────────────┘                              │
│                                                 │
└─────────────────────────────────────────────────┘


PRODUCTION NAMESPACE
────────────────────
Namespace: production
├─ Order Service: 5 pods (powerful nodes)
├─ Payment Service: 5 pods (powerful nodes)
├─ Inventory Service: 3 pods (medium nodes)
├─ CPU limit: 50 cores
├─ Memory limit: 100GB
└─ Network policy: Restricted


STAGING NAMESPACE
─────────────────
Namespace: staging
├─ Order Service: 2 pods
├─ Payment Service: 2 pods
├─ Inventory Service: 1 pod
├─ CPU limit: 10 cores
├─ Memory limit: 20GB
└─ Network policy: Allow all


DEVELOPMENT NAMESPACE
─────────────────────
Namespace: development
├─ Order Service: 1 pod
├─ Payment Service: 1 pod
├─ Inventory Service: 1 pod
├─ CPU limit: 4 cores
├─ Memory limit: 8GB
└─ Network policy: Allow all
```

---

## Ingress - External Access

```
INGRESS ROUTING
═════════════════════════════════════════════════════════════

INTERNET
   ↓
┌─────────────────────────────────────┐
│ Ingress Controller (Load Balancer)  │
│ IP: 35.192.50.100                   │
│                                     │
│ Reads Ingress rules:                │
│ ├─ api.example.com → API Gateway   │
│ ├─ shop.example.com → Shop Svc     │
│ ├─ /admin → Admin Svc              │
│ └─ /api/* → API Svc                │
└─────────────────────────────────────┘
   ↓      ↓      ↓
   ├──────┼──────┘
   ↓      ↓
┌─────────────────────────────────────┐
│ Kubernetes Services                 │
│                                     │
│ API Gateway (8080)                  │
│ Shop Service (3000)                 │
│ Admin Service (8081)                │
│ API Service (8082)                  │
└─────────────────────────────────────┘
   ↓
┌─────────────────────────────────────┐
│ Pods (actual containers)            │
└─────────────────────────────────────┘


TRAFFIC FLOW EXAMPLE
────────────────────
Customer: https://api.example.com/users
  ↓
Ingress: "See api.example.com? Route to API Gateway!"
  ↓
API Gateway Service
  ↓
Picks any available Pod
  ↓
Response back to customer ✅
```

---

## ConfigMap and Secrets

```
CONFIGURATION MANAGEMENT
═════════════════════════════════════════════════════════════

CONFIGMAP (Non-sensitive)
────────────────────────
┌─────────────────────────────────────┐
│ ConfigMap: app-config               │
│                                     │
│ database.host=postgres              │
│ database.port=5432                  │
│ log.level=INFO                      │
│ cache.timeout=300                   │
└─────────────────────────────────────┘
  ↓
Pod mounts as volume at /etc/config
  ↓
Application reads:
  dbHost = readFile('/etc/config/database.host')
  ↓
Connection string built ✅


SECRET (Sensitive)
─────────────────
┌─────────────────────────────────────┐
│ Secret: app-secrets (Encrypted!)    │
│                                     │
│ database.password=abc123xyz         │
│ api.key=secret-key-12345            │
│ jwt.secret=super-secret-token       │
└─────────────────────────────────────┘
  ↓
Pod mounts as volume at /etc/secrets
  ↓
File permissions: Only pod can read
  ↓
Application reads:
  password = readFile('/etc/secrets/database.password')
  ↓
✅ Sensitive data protected!
```

---

## When to Use Kubernetes

```
DO I NEED KUBERNETES?
═════════════════════════════════════════════════════════════

Question 1: Do you have multiple services?
├─ NO  → Skip Kubernetes
└─ YES → Continue...

Question 2: Do they need to scale independently?
├─ NO  → Maybe not Kubernetes
└─ YES → Continue...

Question 3: Do you care about uptime?
├─ NO  → Maybe not Kubernetes
└─ YES → Continue...

Question 4: Will traffic vary?
├─ NO  → Virtual machines fine
└─ YES → Continue...

Question 5: Do you want auto-healing?
├─ NO  → Manual servers fine
└─ YES → KUBERNETES IS PERFECT! ✅


KUBERNETES IS GREAT FOR:
────────────────────────
✅ Microservices architectures
✅ Applications that scale
✅ High availability needs
✅ Cloud deployment
✅ DevOps automation
✅ Containerized applications


KUBERNETES IS NOT GREAT FOR:
────────────────────────────
❌ Single monolithic application
❌ Predictable, static traffic
❌ Very simple apps
❌ Team without Kubernetes expertise
❌ Super low latency requirements
```

---

## Typical Kubernetes Day

```
MORNING
───────
Traffic normal
  └─ 3 pods per service running

NOON (Traffic spike!)
─────
Traffic: 10x normal
  ├─ Autoscaler: "High CPU!"
  ├─ Scale up: 3 → 5 → 8 pods
  └─ All requests handled ✅

AFTERNOON
─────────
One pod crashes
  ├─ Kubernetes: "Pod gone!"
  ├─ Health check failed
  ├─ Auto-restart new pod
  └─ Zero impact ✅

EVENING (Update time)
──────────────────
Deploy new version v2.0
  ├─ Rolling update:
  │  ├─ Stop pod 1, start pod 1 (v2.0)
  │  ├─ Stop pod 2, start pod 2 (v2.0)
  │  └─ Stop pod 3, start pod 3 (v2.0)
  ├─ Zero downtime ✅
  └─ All customers get v2.0

NIGHT (Quiet time)
──────────────────
Traffic drops
  ├─ Autoscaler: "Low CPU!"
  ├─ Scale down: 8 → 5 → 3 pods
  └─ Save resources! 💰

SLEEP
─────
Kubernetes keeps working:
  ├─ Monitoring all pods
  ├─ Recovering from failures
  ├─ Balancing load
  └─ Keeping services healthy ✅
```

---

## Kubernetes vs Manual Management

```
SCENARIO: SERVICE CRASHES

MANUAL MANAGEMENT
─────────────────
Service crashes
  ↓
Monitoring alert (email)
  ↓
DevOps engineer wakes up (2 AM) 😴
  ↓
SSH into server
  ↓
Find the problem
  ↓
Restart service
  ↓
15 minutes downtime ❌
  ↓
Angry customers ❌


KUBERNETES
──────────
Service crashes
  ↓
Kubernetes detects immediately (seconds)
  ↓
Automatically restarts pod
  ↓
New pod ready in seconds
  ↓
Customers don't notice ✅
  ↓
Kubernetes sends info to dashboard
  ↓
DevOps engineer reads it tomorrow
  ↓
Identifies root cause
  ↓
Fixes and redeploys ✅
```

---

## Common Kubernetes Tasks

```
DEVELOPMENT
───────────
Deploy new service:
  kubectl apply -f deployment.yaml

Scale up:
  kubectl scale deployment order-service --replicas=5

View logs:
  kubectl logs order-service-pod-1

Update version:
  kubectl set image deployment/order-service \
    order-service=order-service:v2.0

Delete pod (it restarts):
  kubectl delete pod order-service-pod-1


DEBUGGING
─────────
See pod status:
  kubectl describe pod order-service-pod-1

Check events:
  kubectl get events

View metrics:
  kubectl top pods

Exec into pod:
  kubectl exec -it order-service-pod-1 -- bash
```

---

*Last Updated: 2026-10-01*

*See KUBERNETES_FOR_BEGINNERS.md for simpler overview*

*See KUBERNETES_IN_THIS_PROJECT.md for project-specific details*
