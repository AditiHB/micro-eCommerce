# Kubernetes in the Micro-eCommerce Project 🏪

## The Big Picture

This project uses Kubernetes to automatically manage all microservices. Each service runs in containers, and Kubernetes keeps them healthy, scales them, and routes traffic!

---

## The Services in Kubernetes 🔗

```
┌──────────────────────────────────────────────┐
│                                              │
│   📦 Order Service                           │
│   💳 Payment Service                         │
│   📊 Inventory Service   ← Kubernetes →      │
│   📧 Notification Service                    │
│   🚚 Delivery Service                        │
│   🚪 API Gateway                             │
│                                              │
└──────────────────────────────────────────────┘
```

---

## How Kubernetes Manages Each Service 🎯

### Order Service Deployment

**Declaration (What we tell Kubernetes):**
```yaml
Deployment: OrderService
├─ Always keep: 3 pods running
├─ Container image: order-service:latest
├─ Memory needed: 512MB per pod
├─ CPU needed: 250m per pod
└─ Port: 8080
```

**What Kubernetes does:**
```
Kubernetes sees this and:
✅ Creates 3 Order Service containers
✅ Monitors all 3
✅ If one dies → Creates a replacement
✅ If load increases → Creates more automatically
✅ Routes all requests to available pods
```

### Payment Service Deployment

**Declaration:**
```yaml
Deployment: PaymentService
├─ Always keep: 2 pods running
├─ Container image: payment-service:latest
├─ Memory needed: 1GB per pod
├─ CPU needed: 500m per pod
└─ Port: 8081
```

**What happens:**
```
Payment Service needs more resources (1GB each)
       ↓
Kubernetes puts them on powerful nodes
       ↓
2 pods always running and healthy
       ↓
If busy → Auto-scales to 5 pods
       ↓
If quiet → Scales back to 2 pods
```

---

## Services Communicating 🔗

### Internal Communication (Pod to Pod)

```
Order Pod 1 needs to call Payment Service
       ↓
Asks Kubernetes: "Where is PaymentService?"
       ↓
Kubernetes Service: "Here's the stable address: payment-service:8081"
       ↓
Order Pod gets routed to any available Payment Pod
       ↓
Payment Pod processes the request
```

### External Communication (Customer to API)

```
Customer in browser
       ↓
Calls: your-app.com
       ↓
Kubernetes Ingress Controller
       ↓
Routes to API Gateway Service
       ↓
API Gateway Service load-balances to any available Gateway Pod
       ↓
API Gateway routes to correct backend service
```

---

## Pods and Deployments 📦

### What is a Pod?

```
1 Pod = 1 or more containers (usually 1)

Order Service Pod:
└─ Container: order-service application
   ├─ Code
   ├─ Dependencies
   ├─ Configuration
   └─ Runs on a Node (physical machine)
```

### What is a Deployment?

```
Deployment: "Keep Order Service healthy"

Deployment Controller watches:
┌─────────────────────────────────────┐
│ ORDER SERVICE DEPLOYMENT            │
│ Desired: 3 pods                     │
│ Current: 3 pods                     │
│                                     │
│ Pod 1: Running ✅                   │
│ Pod 2: Running ✅                   │
│ Pod 3: Running ✅                   │
└─────────────────────────────────────┘

If Pod 1 crashes:
   ↓
Deployment: "Should be 3, only 2 running!"
   ↓
Creates new Pod 4
   ↓
Back to 3 running!
```

---

## Nodes (The Kitchens) 🏪

### What is a Node?

```
Node = Physical or virtual machine

Node 1 (Powerful):
├─ Payment Pod 1 (needs 1GB memory)
├─ Payment Pod 2 (needs 1GB memory)
└─ 4GB memory, 4 CPU cores

Node 2 (Medium):
├─ Order Pod 1 (needs 512MB)
├─ Order Pod 2 (needs 512MB)
├─ Notification Pod 1 (needs 256MB)
└─ 2GB memory, 2 CPU cores

Node 3 (Small):
├─ Inventory Pod 1 (needs 512MB)
└─ 1GB memory, 1 CPU core
```

### Kubernetes Scheduler

```
New Pod needs to be created
       ↓
Kubernetes Scheduler checks all nodes:
   Node 1: Full ❌
   Node 2: Enough space ✅
   Node 3: Not enough memory ❌
       ↓
Scheduler: "Put it on Node 2"
       ↓
Pod created on Node 2
```

---

## The Deployment Lifecycle 🔄

### Creating a Service

```
Step 1: Create Deployment definition
   ↓ (YAML file)
Step 2: kubectl apply -f deployment.yaml
   ↓
Step 3: Kubernetes creates 3 pods
   ↓ (Based on desired replicas)
Step 4: Pods start on available nodes
   ↓
Step 5: Kubernetes Service created
   ↓ (Stable address for pods)
Step 6: Pods ready to receive requests
   ↓
🎉 Service running!
```

### Updating a Service (Rolling Update)

```
Step 1: New version available (v2.0)
   ↓
Step 2: Update Deployment with new image
   ↓
Step 3: Kubernetes does rolling update:
   
   OLD: [Pod1 v1.0] [Pod2 v1.0] [Pod3 v1.0]
       ↓
   STEP 1: [Pod1 v2.0] [Pod2 v1.0] [Pod3 v1.0]
       ↓
   STEP 2: [Pod1 v2.0] [Pod2 v2.0] [Pod3 v1.0]
       ↓
   STEP 3: [Pod1 v2.0] [Pod2 v2.0] [Pod3 v2.0]
       ↓
Step 4: All updated, zero downtime! ✅
```

---

## Services (Stable Addresses) 📞

### Service Types

#### 1. ClusterIP (Internal Only)
```
Service: payment-service (ClusterIP)
├─ Internal address: 10.0.0.5:8081
├─ Only accessible within cluster
└─ Routes to Payment Pods

Order Pod:
   ↓
Calls: http://payment-service:8081
   ↓
Service load-balances to:
   Pod A or Pod B or Pod C (whichever is free)
```

#### 2. NodePort (Expose to Outside)
```
Service: api-gateway (NodePort)
├─ External port: 30000
├─ Maps to: api-gateway Service
└─ Accessible from outside cluster

External:
   ↓
Calls: 192.168.1.100:30000
   ↓
Kubernetes routes to API Gateway Pod
```

#### 3. LoadBalancer (Best for Production)
```
Service: api-gateway (LoadBalancer)
├─ Cloud provider load balancer
├─ External IP: 35.192.50.100
└─ Accessible from anywhere

Customer:
   ↓
Calls: 35.192.50.100
   ↓
Load balancer distributes to Nodes
   ↓
API Gateway handles request
```

---

## Auto-Scaling 📈

### How Kubernetes Scales

```
Metrics monitoring:
   ↓
CPU usage: 80% 🔴
Memory usage: 75% 🟠
   ↓
Kubernetes Autoscaler checks limits:
   "Service says max 5 pods"
   "Current: 3 pods"
   ↓
Decision: Scale up!
   ↓
Create new pod
   ↓
Now: 4 pods
   ↓
Monitor again...

After traffic goes down:
   ↓
CPU usage: 30% ✅
Memory usage: 25% ✅
   ↓
Kubernetes: "We have too many pods"
   ↓
Scale down to 2 pods
   ↓
Save resources and money! 💰
```

---

## Persistent Data (Volumes) 💾

### Why Pods Need Storage

```
Problem: Pods are temporary
   ↓
Pod deleted or moved
   ↓
Data inside pod GONE! ❌
   ↓
Solution: Kubernetes Volumes
```

### Volume Types

```
Type 1: emptyDir
├─ Temporary storage
├─ Deleted when pod deleted
└─ Good for: Caches, temp files

Type 2: hostPath
├─ Storage on host node
├─ Survives pod restart
└─ Good for: Single node setups

Type 3: PersistentVolume (PV)
├─ Real storage (cloud storage)
├─ Survives node restart
└─ Good for: Production databases

Example:
Database Pod needs storage
   ↓
PersistentVolumeClaim: "I need 10GB"
   ↓
Kubernetes: "Creating 10GB storage volume"
   ↓
Pod mounts it: /data
   ↓
Data survives pod restart ✅
```

---

## ConfigMaps and Secrets 🔐

### ConfigMap (Non-sensitive config)

```
ConfigMap: app-config
├─ DATABASE_HOST=postgres.default
├─ DATABASE_PORT=5432
├─ LOG_LEVEL=INFO
└─ API_TIMEOUT=30s

Order Service Pod:
   ↓
Mounts ConfigMap as volume
   ↓
Reads configuration values
   ↓
Uses them in application
```

### Secret (Sensitive data)

```
Secret: app-secrets
├─ DATABASE_PASSWORD=super-secret-123
├─ API_KEY=abcd1234efgh5678
└─ PAYMENT_TOKEN=xyz789

Payment Service Pod:
   ↓
Mounts Secret (encrypted in etcd)
   ↓
Reads sensitive values
   ↓
Never exposed in logs or configs
```

---

## Health Checks 🏥

### Liveness Probe (Is it alive?)

```
Payment Service Pod:
   ↓
Kubernetes checks every 10 seconds:
   ↓
Send request to: /health/live
   ↓
Response 200 OK ✅
   Pod is alive!
   ↓
Response timeout ❌
   Pod not responding!
   ↓
Kubernetes: Kill and restart this pod
```

### Readiness Probe (Can it handle traffic?)

```
Order Service Pod just started:
   ↓
Kubernetes checks every 5 seconds:
   ↓
Send request to: /health/ready
   ↓
Response 200 OK ✅
   Pod is ready! Start sending traffic
   ↓
Response 500 ❌
   Pod not ready! Don't send traffic yet
   ↓
Wait 10 more seconds and check again
```

---

## Namespaces (Virtual Clusters) 🏘️

### What is a Namespace?

```
Cluster has 3 namespaces:

Namespace: production
├─ Order Service: 5 pods
├─ Payment Service: 5 pods
└─ Inventory Service: 3 pods

Namespace: staging
├─ Order Service: 2 pods
├─ Payment Service: 2 pods
└─ Inventory Service: 1 pod

Namespace: development
├─ Order Service: 1 pod
├─ Payment Service: 1 pod
└─ Inventory Service: 1 pod
```

### Benefits

```
✅ Isolation: Dev can't affect Production
✅ Resource quotas: Dev gets 2 cores, Prod gets 16 cores
✅ Policies: Different rules per namespace
✅ Organization: Separate teams' services
```

---

## Monitoring and Logging 📊

### What Kubernetes Tracks

```
Metrics collected for each pod:
├─ CPU usage
├─ Memory usage
├─ Network traffic
├─ Disk I/O
├─ Restart count
└─ Age

Alerts triggered when:
├─ Pod crashes (>3 restarts/hour)
├─ Memory usage >90%
├─ CPU usage >80%
├─ Pod not ready for >5 minutes
└─ Node unreachable
```

### Logging

```
Each pod's logs collected by Kubernetes:
   ↓
kubectl logs order-service-pod-1
   ↓
Shows all output from that pod
   ↓
Useful for debugging issues!
```

---

## Ingress (External Access) 🚪

### What is Ingress?

```
┌─────────────────────────────────────┐
│ OUTSIDE WORLD (Internet)            │
└─────────────────────────────────────┘
           ↓
┌─────────────────────────────────────┐
│ Ingress Controller (Load balancer)  │
│ Reads Ingress rules                 │
└─────────────────────────────────────┘
           ↓
Routes based on:
├─ Hostname: api.example.com → API Gateway
├─ Path: /admin → Admin Service
└─ Path: /shop → Shop Service
           ↓
┌─────────────────────────────────────┐
│ Kubernetes Services                 │
│ (Internal network)                  │
└─────────────────────────────────────┘
```

---

## Typical Deployment Flow in This Project 🚀

```
Developer pushes code to GitHub
       ↓
CI/CD Pipeline runs tests
       ↓
Tests pass ✅
       ↓
Build new container image
       ↓
Push to Docker Registry
       ↓
Update Kubernetes Deployment YAML
   image: order-service:v2.0
       ↓
kubectl apply -f deployment.yaml
       ↓
Kubernetes starts rolling update:
   Old pods: v1.0
   New pods: v2.0 (gradual replacement)
       ↓
All pods updated with zero downtime! ✅
       ↓
Service running v2.0
       ↓
✅ Deployment complete!
```

---

## Commands You'll Use 💻

```bash
# See all pods
kubectl get pods

# Describe a pod
kubectl describe pod order-service-pod-1

# View pod logs
kubectl logs order-service-pod-1

# Scale a deployment
kubectl scale deployment order-service --replicas=5

# Update a deployment
kubectl set image deployment/order-service \
  order-service=order-service:v2.0

# See services
kubectl get svc

# See deployments
kubectl get deployments

# Delete a pod (it will restart)
kubectl delete pod order-service-pod-1
```

---

## Common Scenarios 🎯

### Scenario 1: Traffic Spike
```
Traffic increases 10x
       ↓
Kubernetes detects high CPU/Memory
       ↓
Autoscaler creates new pods
   1 pod → 3 pods → 10 pods
       ↓
Load balancer distributes requests
       ↓
All customers get responses! ✅
```

### Scenario 2: Pod Crash
```
Order Service Pod crashes 💥
       ↓
Kubernetes liveness probe fails
       ↓
Kubernetes: "Pod is dead, replacing it"
       ↓
Creates new pod
       ↓
Customers don't notice! ✅
```

### Scenario 3: Node Dies
```
Node 1 goes down completely 🔥
       ↓
Kubernetes detects: "Node 1 unreachable"
       ↓
Reschedules all pods from Node 1 to Node 2 & 3
       ↓
Everything still running! ✅
```

### Scenario 4: Update to New Version
```
New order-service v2.0 released
       ↓
Update deployment:
   image: order-service:v2.0
       ↓
Kubernetes rolling update:
   Stop 1 old pod
   Start 1 new pod (v2.0)
   Repeat for all pods
       ↓
Zero downtime! ✅
```

---

## Why Kubernetes Matters for Your Project 💡

### Without Kubernetes
```
Each service on separate servers:
   Server 1: Order Service (static)
   Server 2: Payment Service (static)
   Server 3: Inventory Service (static)
   
Problems:
❌ Server crashes? Manual intervention
❌ Traffic spike? Manual scaling
❌ Update service? Downtime
❌ Adding service? Buy new server
❌ Wasted resources when traffic is low
```

### With Kubernetes
```
All services in Kubernetes cluster:
   
Benefits:
✅ Automatic recovery from crashes
✅ Automatic scaling based on traffic
✅ Zero-downtime updates
✅ Add services easily (just deploy)
✅ Efficient resource usage
✅ Monitoring and logging built-in
✅ High availability out of the box
```

---

*Happy Learning! 🎉*

*For more Kubernetes concepts, see KUBERNETES_FOR_BEGINNERS.md*

*Last Updated: 2026-10-01*
