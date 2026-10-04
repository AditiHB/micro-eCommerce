# Kubernetes Zero to Hero 🚀

Complete master guide to mastering Kubernetes! From basics to advanced production patterns.

---

## Part 1: Foundation (Already Covered, Quick Review)

### The Basics You Know ✅
```
Container → Pod → Deployment → Service → Ingress
   ↓        ↓        ↓           ↓        ↓
 App in   Wrapper  Manager    Network  Route
  Box              Replicas    Endpoint Traffic
```

**Core Concepts:**
- **Container:** Application in a box
- **Pod:** Smallest unit (wraps containers)
- **Deployment:** Manages pod replicas
- **Service:** Stable network access
- **Node:** Machine running pods
- **Cluster:** Group of nodes
- **Namespace:** Virtual cluster
- **Health Checks:** Liveness & Readiness probes
- **ConfigMaps:** Non-sensitive configuration
- **Secrets:** Sensitive data (passwords, keys)
- **Volumes:** Storage for containers

---

## Part 2: Intermediate Concepts 🎯

### 1. Ingress - External Access 🌐

**The Problem:**
```
Services are internal (accessible inside cluster only)
How do external users access your app?
```

**The Solution: Ingress**
```
        INTERNET
            ↓
   ┌─────────────────┐
   │  Ingress Object │ (Routes HTTP/HTTPS traffic)
   └────────┬────────┘
            ↓
   ┌─────────────────┐
   │ Ingress Controller│ (Nginx/HAProxy - actually routes)
   └────────┬────────┘
            ↓
   ┌────────────────────────┐
   │ Services (internal)    │
   │ ├─ api.example.com     │
   │ ├─ web.example.com     │
   │ └─ admin.example.com   │
   └────────┬───────────────┘
            ↓
   ┌────────────────────────┐
   │ Pods (running apps)    │
   └────────────────────────┘
```

**Ingress Configuration:**
```yaml
apiVersion: networking.k8s.io/v1
kind: Ingress
metadata:
  name: micro-ecommerce-ingress
spec:
  rules:
  - host: api.example.com
    http:
      paths:
      - path: /
        pathType: Prefix
        backend:
          service:
            name: order-service
            port:
              number: 8080
  
  - host: payment.example.com
    http:
      paths:
      - path: /
        pathType: Prefix
        backend:
          service:
            name: payment-service
            port:
              number: 8081
```

**Traffic Flow:**
```
User: https://api.example.com/orders
         ↓
   Ingress Controller
         ↓
   Route to order-service (port 8080)
         ↓
   order-service sends to one of 3 pods
         ↓
   Pod processes request
         ↓
   Response back to user ✅
```

**In the Project:**
```
This project doesn't use Ingress at all - api-gateway's own Service is
type: LoadBalancer (k8s/base/07-api-gateway.yaml), and it does the
path-based routing itself (uri: lb://order-service, Path=/api/orders/**,
etc. - already baked into its image). k8s/nginx-https is an optional,
not-yet-verified alternative front door (see its own README), also not an
Ingress resource - a plain nginx Deployment doing its own proxy_pass.
```

---

### 2. StatefulSets - Stateful Applications 📊

**The Problem with Deployments:**
```
Deployment creates pods:
├─ Pod 1 (name: order-abc123)
├─ Pod 2 (name: order-xyz789)
└─ Pod 3 (name: order-qrs456)

Problem: Pod names random, no identity, no stable storage
Pods die and respawn with different names

Issue for databases:
├─ Pod 1 has data: [User A, User B, User C]
├─ Pod 1 dies
├─ Pod 4 respawns with empty data
└─ All data lost! 💥
```

**StatefulSet Solution:**
```
StatefulSet creates pods with identity:
├─ Pod 0: mysql-0 (stable name!)
├─ Pod 1: mysql-1 (stable name!)
└─ Pod 2: mysql-2 (stable name!)

Each has persistent storage:
├─ mysql-0 → Volume on Node A
├─ mysql-1 → Volume on Node B
└─ mysql-2 → Volume on Node C

If pod dies:
├─ mysql-0 respawns
├─ Reconnects to same volume
├─ Data still there! ✅
```

**When to Use StatefulSet:**
```
✅ Databases (MySQL, PostgreSQL, MongoDB)
✅ Message queues (RabbitMQ, Kafka)
✅ Search engines (Elasticsearch)
✅ Any app needing stable identity + storage

❌ Stateless apps (use Deployment)
❌ Web servers (use Deployment)
❌ APIs (use Deployment)
```

**StatefulSet Example:**
```yaml
apiVersion: apps/v1
kind: StatefulSet
metadata:
  name: mysql-db
spec:
  serviceName: mysql
  replicas: 3
  selector:
    matchLabels:
      app: mysql
  template:
    metadata:
      labels:
        app: mysql
    spec:
      containers:
      - name: mysql
        image: mysql:8.0
        ports:
        - containerPort: 3306
        volumeMounts:
        - name: mysql-storage
          mountPath: /var/lib/mysql
  volumeClaimTemplates:
  - metadata:
      name: mysql-storage
    spec:
      accessModes: [ "ReadWriteOnce" ]
      resources:
        requests:
          storage: 10Gi
```

**In the Project:**
```
Deployments (Stateless - k8s/base):
├─ api-gateway, customer/order/inventory/payment/notification-service
└─ discovery-server, config-server, zookeeper, kafka

StatefulSets (Stateful):
├─ redis (k8s/base/04-redis.yaml)
└─ postgres (k8s/overlays/postgres/postgres.yaml - only under that profile;
   overlays/h2 has no StatefulSet at all, each service's own in-process H2
   lives inside its stateless pod)
```
No Delivery Service - that's not a real part of this project.

---

### 3. DaemonSets - Run on Every Node 🔄

**What is a DaemonSet?**
```
Deployment: 3 replicas → 3 pods anywhere
            ↓
DaemonSet: Runs on EVERY node
           ↓
           6 nodes → 6 pods (one per node)
           10 nodes → 10 pods (one per node)
           1000 nodes → 1000 pods (one per node)
```

**Use Cases:**
```
✅ Logging (collect logs from every node)
✅ Monitoring (monitor every node)
✅ Networking (network plugin on every node)
✅ Security (antivirus on every node)
✅ Backup (backup data from every node)

DaemonSet Examples:
├─ Prometheus Node Exporter (metrics)
├─ Filebeat (log collection)
├─ Calico (networking)
├─ Datadog Agent (monitoring)
└─ Falco (security monitoring)
```

**DaemonSet Example:**
```yaml
apiVersion: apps/v1
kind: DaemonSet
metadata:
  name: log-collector
spec:
  selector:
    matchLabels:
      app: log-collector
  template:
    metadata:
      labels:
        app: log-collector
    spec:
      containers:
      - name: log-collector
        image: elastic/filebeat:8.0
        volumeMounts:
        - name: container-logs
          mountPath: /var/lib/docker/containers
          readOnly: true
      volumes:
      - name: container-logs
        hostPath:
          path: /var/lib/docker/containers
```

---

### 4. Jobs and CronJobs - Run Once or Scheduled ⏰

**Jobs - Run Once:**
```
Job: Do something once, then stop

Examples:
├─ Database migrations (run once)
├─ Data backups (run once)
├─ Report generation (run once)
└─ Cleanup tasks (run once)

Job lifecycle:
1. Create pod
2. Run task
3. Task completes
4. Pod stops
5. Job marks successful ✅
```

**Job Example:**
```yaml
apiVersion: batch/v1
kind: Job
metadata:
  name: db-migration
spec:
  template:
    spec:
      containers:
      - name: migrate
        image: myapp:v2
        command: ["python", "migrate.py"]
      restartPolicy: Never
  backoffLimit: 3  # Retry 3 times if fails
```

**CronJob - Scheduled:**
```
CronJob: Do something on a schedule

Examples:
├─ Daily backups (2 AM every day)
├─ Weekly reports (Monday 9 AM)
├─ Hourly data syncs
└─ Monthly cleanup

Cron syntax:
minute hour day month day-of-week
  ↓    ↓   ↓   ↓     ↓
  0    2   *   *     *      (2 AM every day)
  0    0   *   *     1      (Monday midnight)
  0    */6 *   *     *      (Every 6 hours)
  0    9   1   *     *      (1st of month at 9 AM)
```

**CronJob Example:**
```yaml
apiVersion: batch/v1
kind: CronJob
metadata:
  name: daily-backup
spec:
  schedule: "0 2 * * *"  # 2 AM every day
  jobTemplate:
    spec:
      template:
        spec:
          containers:
          - name: backup
            image: myapp:latest
            command: ["bash", "backup.sh"]
          restartPolicy: OnFailure
```

**In the Project:**
```
No Job or CronJob objects exist in this project's k8s/ at all. Database
migration (Flyway) isn't a separate Job here - it runs automatically as
part of each service's own Spring Boot startup, against whichever
datasource profile is active (H2 or Postgres - see
docs/KUBERNETES_DEPLOYMENT.md's "Switching to Postgres"). No backup or
reporting CronJob is deployed either.
```

---

### 5. Namespaces - Isolation & Organization 🏢

**The Problem:**
```
Many apps in one cluster = namespace chaos!

Without namespaces:
├─ All pods in same space
├─ Naming conflicts (2 "api" pods?)
├─ Hard to isolate teams
├─ Hard to apply different policies
└─ Everyone can access everything
```

**Namespace Solution:**
```
Cluster
├─ Namespace: production
│  ├─ order-service
│  ├─ payment-service
│  └─ database
│
├─ Namespace: staging
│  ├─ order-service (test version)
│  ├─ payment-service (test version)
│  └─ database (test data)
│
├─ Namespace: monitoring
│  ├─ prometheus
│  └─ grafana
│
└─ Namespace: default
   (system stuff)
```

**Benefits:**
```
✅ Organization (logical grouping)
✅ Isolation (prod separate from staging)
✅ Multi-tenancy (team A doesn't affect team B)
✅ Resource quotas (limit resources per namespace)
✅ Network policies (isolate traffic)
✅ RBAC (different permissions per namespace)
```

**Common Namespaces:**
```
default - System stuff, not recommended
kube-system - Kubernetes system pods
kube-public - Public info
kube-node-lease - Node heartbeats
production - Production apps
staging - Staging/testing apps
monitoring - Prometheus, Grafana
logging - Log collection (ELK, EFK)
ingress-nginx - Ingress controller
```

**Using Namespaces:**
```bash
# Create namespace
kubectl create namespace production

# Deploy to namespace
kubectl apply -f order-service.yaml -n production

# List pods in namespace
kubectl get pods -n production

# Delete everything in namespace
kubectl delete all -n staging
```

**Namespace Example YAML:**
```yaml
apiVersion: v1
kind: Namespace
metadata:
  name: production
---
apiVersion: v1
kind: ResourceQuota
metadata:
  name: production-quota
  namespace: production
spec:
  hard:
    requests.cpu: "100"
    requests.memory: "200Gi"
    limits.cpu: "200"
    limits.memory: "400Gi"
```

---

### 6. Persistent Volumes (PV) & PersistentVolumeClaims (PVC) 💾

**The Problem:**
```
Pod has storage: /data
Pod dies
Pod respawns with empty /data
Old data gone! 💥

Solution: Persistent storage outside pod
```

**How It Works:**
```
┌─────────────────────────────────────┐
│ Cluster Admin                       │
│                                     │
│ Creates: PersistentVolume (PV)      │
│ ├─ Type: AWS EBS, NFS, Local       │
│ ├─ Size: 100 GB                    │
│ └─ Status: Available               │
└─────────────────────────────────────┘
              ↑
┌─────────────────────────────────────┐
│ Developer                           │
│                                     │
│ Creates: PersistentVolumeClaim (PVC)│
│ ├─ Request: 50 GB                  │
│ └─ Access: ReadWriteOnce            │
└─────────────────────────────────────┘
              ↑
   Kubernetes finds PV matching request
   ├─ PVC binds to PV
   └─ Pod uses PVC → accesses PV data ✅
```

**PV Types:**
```
Local:
├─ Data on machine's disk
├─ Fast ✨
├─ Problem: If node dies, data lost
└─ Use: Development, caching

NFS (Network File System):
├─ Shared storage
├─ Multiple pods access same data
└─ Use: Shared files

AWS EBS:
├─ Cloud storage
├─ Survives node death
└─ Use: Production databases

AWS EFS:
├─ Managed NFS
├─ Multiple pods access
└─ Use: Shared production storage
```

**PV & PVC Example:**
```yaml
# Admin creates PersistentVolume
apiVersion: v1
kind: PersistentVolume
metadata:
  name: mysql-pv
spec:
  capacity:
    storage: 100Gi
  accessModes:
    - ReadWriteOnce
  storageClassName: fast
  awsElasticBlockStore:
    volumeID: vol-123456
    fsType: ext4

---
# Developer creates PersistentVolumeClaim
apiVersion: v1
kind: PersistentVolumeClaim
metadata:
  name: mysql-pvc
  namespace: production
spec:
  accessModes:
    - ReadWriteOnce
  storageClassName: fast
  resources:
    requests:
      storage: 50Gi

---
# Pod uses PVC
apiVersion: v1
kind: Pod
metadata:
  name: mysql-pod
spec:
  containers:
  - name: mysql
    image: mysql:8.0
    volumeMounts:
    - name: mysql-storage
      mountPath: /var/lib/mysql
  volumes:
  - name: mysql-storage
    persistentVolumeClaim:
      claimName: mysql-pvc
```

---

### 7. RBAC - Role-Based Access Control 🔐

**The Problem:**
```
Everyone can do everything!

Developer A: Can delete production database ❌
DevOps intern: Can modify payment service ❌
Everyone: Can access production secrets ❌
```

**RBAC Solution:**
```
┌──────────────────────────────────────┐
│ RBAC Hierarchy                       │
├──────────────────────────────────────┤
│ User/ServiceAccount                  │
│   ↓                                  │
│ RoleBinding/ClusterRoleBinding       │
│   ↓                                  │
│ Role/ClusterRole                     │
│   ↓                                  │
│ Actions (can: get, list, create...) │
└──────────────────────────────────────┘
```

**Roles:**
```
Admin Role:
├─ Can: get, list, create, update, delete
├─ Resources: all
└─ Namespace: specific

Read-Only Role:
├─ Can: get, list
├─ Resources: pods, services
└─ Namespace: specific

Viewer Role:
├─ Can: get
├─ Resources: logs
└─ Namespace: all

Developer Role:
├─ Can: get, list, create, update
├─ Resources: pods, services, configmaps
├─ Cannot: delete, modify RBAC
└─ Namespace: production
```

**RBAC Example:**
```yaml
# Define what actions are allowed
apiVersion: rbac.authorization.k8s.io/v1
kind: Role
metadata:
  name: developer
  namespace: production
rules:
- apiGroups: [""]
  resources: ["pods", "pods/logs", "services"]
  verbs: ["get", "list", "watch", "create", "update", "patch"]
- apiGroups: [""]
  resources: ["configmaps"]
  verbs: ["get", "list"]
# Cannot delete, cannot access secrets!

---
# Assign role to user
apiVersion: rbac.authorization.k8s.io/v1
kind: RoleBinding
metadata:
  name: developer-binding
  namespace: production
roleRef:
  apiGroup: rbac.authorization.k8s.io
  kind: Role
  name: developer
subjects:
- kind: User
  name: alice@company.com
  apiGroup: rbac.authorization.k8s.io
```

**In the Project:**
```
No human/team RBAC exists here (no DevOps/Developer/QA Role bound to a
User) - everything in k8s/base/02-rbac.yaml is per-service:

customer-service-sa (and one more per service):
├─ Role: read-ecommerce-secrets
├─ Can: get, on Secret "ecommerce-secrets" only
└─ Namespace: ecommerce

Worth being honest about: none of these services actually call the
Kubernetes API themselves, so this Role doesn't change what the app can do
- secretKeyRef env injection is resolved by the kubelet, not the pod's own
RBAC. It's a genuine, working least-privilege example, not something this
app's code currently exercises.
```

---

### 8. Network Policies - Isolate Traffic 🔒

**The Problem:**
```
All pods can talk to all pods by default

Pod A can access:
├─ Pod B (payment) - Should allow ✓
├─ Pod C (inventory) - Should allow ✓
└─ Pod X (external malware) - Should block ✗
```

**Network Policy Solution:**
```
Whitelist: Only allow these connections

Pod A can connect to:
├─ Pod B (payment) ✅
└─ Pod C (inventory) ✅

Pod A cannot connect to:
├─ Pod X ❌
└─ Anything else ❌
```

**Network Policy Example:**
```yaml
apiVersion: networking.k8s.io/v1
kind: NetworkPolicy
metadata:
  name: allow-order-to-payment
  namespace: production
spec:
  podSelector:
    matchLabels:
      app: payment
  policyTypes:
  - Ingress
  ingress:
  - from:
    - podSelector:
        matchLabels:
          app: order
    ports:
    - protocol: TCP
      port: 8080

# This means:
# Payment pods can receive traffic from order pods
# On port 8080 only
# Everything else is blocked!
```

**Network Policy Types:**
```
Ingress: Control incoming traffic
├─ Allow from Pod A to Pod B
└─ Block from Pod X to Pod B

Egress: Control outgoing traffic
├─ Allow Pod A to reach Pod B
└─ Block Pod A from reaching Pod X
```

---

### 9. Resource Requests & Limits 💾

**The Problem:**
```
Without requests/limits:

Pod A uses: 16 GB RAM (entire node!)
Pod B: Can't run (no memory!)
Cluster wasted ❌

Scheduler: Doesn't know where to put Pod B
```

**Requests: Guaranteed Resources**
```
Request = "I need this much to run"

Pod spec:
resources:
  requests:
    memory: "256Mi"  # Need 256 MB RAM
    cpu: "250m"      # Need 0.25 CPU

Scheduler:
├─ Finds node with 256 MB free
├─ Reserves it for this pod
└─ Pod gets what it asked for ✅
```

**Limits: Maximum Resources**
```
Limit = "Don't give me more than this"

Pod spec:
resources:
  limits:
    memory: "512Mi"  # Max 512 MB RAM
    cpu: "500m"      # Max 0.5 CPU

If pod exceeds:
├─ Memory: Pod killed (OOMKilled)
├─ CPU: Pod throttled (slowed down)
└─ Prevents runaway usage ✅
```

**Example:**
```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: order-service
spec:
  replicas: 3
  template:
    spec:
      containers:
      - name: order-service
        image: order-service:v1
        resources:
          requests:
            memory: "256Mi"    # Guaranteed
            cpu: "250m"        # Guaranteed
          limits:
            memory: "512Mi"    # Maximum
            cpu: "500m"        # Maximum
```

**In the Project:**
```
api-gateway + all 5 business services (k8s/base):
├─ Request: 256Mi memory, 250m CPU
└─ Limit: 512Mi memory, 500m CPU

kafka (k8s/base/03-messaging.yaml - the heaviest thing here):
├─ Request: 512Mi memory, 250m CPU
└─ Limit: 1Gi memory, 1000m CPU

redis / zookeeper:
├─ Request: 128-256Mi memory, 100m CPU
└─ Limit: 512Mi memory, 500m CPU

postgres (k8s/overlays/postgres only):
├─ Request: 256Mi memory, 250m CPU
└─ Limit: 1Gi memory, 1000m CPU
```
These are what the HPA's 70%-of-request CPU target in
k8s/base/13-hpa.yaml actually scales against - see
docs/KUBERNETES_DEPLOYMENT.md's "Scaling" section for the real load test.

---

## Part 3: Advanced Patterns 💪

### Taints & Tolerations - Special Nodes 🏷️

**The Problem:**
```
Some nodes should only run certain pods

GPU node should run:
├─ ML training pods ✓
└─ NOT regular API servers ✗

But Kubernetes doesn't know!
Regular pods get scheduled on GPU node 💰💰
```

**Taint: Mark the Node**
```
Admin taint GPU node:
"Only ML pods allowed here"

Kubernetes:
├─ Refuses to schedule regular pods
└─ Only schedules pods with matching toleration ✓
```

**Toleration: Pod Says "I Accept"**
```
ML Training Pod:
"I tolerate the 'gpu' taint
 Let me run on the GPU node"

Kubernetes:
└─ Schedules it on GPU node ✅
```

**Example:**
```bash
# Admin taints GPU node
kubectl taint nodes gpu-node gpu=true:NoSchedule

# Now regular pods won't go there
---

# ML pod tolerates it
apiVersion: v1
kind: Pod
metadata:
  name: ml-training
spec:
  tolerations:
  - key: gpu
    operator: Equal
    value: "true"
    effect: NoSchedule
  nodeSelector:
    gpu: "true"
  containers:
  - name: trainer
    image: ml:latest
```

---

### Init Containers - Run First 🚀

**The Problem:**
```
Pod needs setup before main app starts

Examples:
├─ Database migration (wait for DB)
├─ Config download (before starting)
├─ Secret injection (before starting)
└─ Wait for other services (dependencies)
```

**Init Container: Run First**
```
Pod startup:
1. Run init container 1 ✅
2. Init container completes
3. Run init container 2 ✅
4. Init container completes
5. NOW run main container ✅

If init fails:
├─ Pod restarts
├─ Init runs again
└─ Retry until success
```

**Example:**
```yaml
apiVersion: v1
kind: Pod
metadata:
  name: order-service
spec:
  initContainers:
  - name: wait-for-db
    image: busybox
    command: ["sh", "-c", "until nc -z db 5432; do sleep 1; done"]
  
  - name: db-migrate
    image: order-service:v1
    command: ["python", "migrate.py"]
  
  containers:
  - name: order-service
    image: order-service:v1
    ports:
    - containerPort: 8080
```

---

### Horizontal Pod Autoscaler (HPA) 📈

**Auto-Scaling:**
```
Current: 2 replicas
CPU: 80% (high!)

HPA decision:
├─ Scale up to 4 replicas
├─ CPU drops to 45% ✅

Later: Traffic drops
├─ CPU: 20% (low!)
├─ Scale down to 1 replica
└─ Save costs! 💰
```

**HPA Example:**
```yaml
apiVersion: autoscaling/v2
kind: HorizontalPodAutoscaler
metadata:
  name: order-service-hpa
spec:
  scaleTargetRef:
    apiVersion: apps/v1
    kind: Deployment
    name: order-service
  minReplicas: 2
  maxReplicas: 10
  metrics:
  - type: Resource
    resource:
      name: cpu
      target:
        type: Utilization
        averageUtilization: 70
  - type: Resource
    resource:
      name: memory
      target:
        type: Utilization
        averageUtilization: 80
```

**Scaling Logic:**
```
If CPU > 70%: Add replicas
If CPU < 30%: Remove replicas
If Memory > 80%: Add replicas
If Memory < 50%: Remove replicas

Check every 15 seconds (default)
Make gradual changes (no sudden jumps)
```

---

### Pod Disruption Budgets (PDB) 🛡️

**The Problem:**
```
Cluster maintenance: Need to restart nodes

System: Shuts down all pods on node
Order Service: ALL 3 replicas down! 😱
Customers: Can't place orders
```

**PDB: Minimum Availability**
```
"At least 2 Order Service pods must always run"

Node shutdown:
├─ Check PDB
├─ Can we shut down this pod?
├─ Will we violate minimum?
├─ If yes: Wait (or restart pod elsewhere)
└─ If no: Shut down pod ✅
```

**Example:**
```yaml
apiVersion: policy/v1
kind: PodDisruptionBudget
metadata:
  name: order-service-pdb
spec:
  minAvailable: 2  # At least 2 pods
  selector:
    matchLabels:
      app: order-service
```

---

## Part 4: Production Checklist ✅

```
CLUSTER SETUP ✅
├─ Multiple nodes (3+ for HA)
├─ Multiple zones/availability zones
├─ Network policies configured
├─ Storage class configured
└─ Ingress controller deployed

DEPLOYMENTS ✅
├─ Resource requests set
├─ Resource limits set
├─ Health checks configured
├─ Replicas >= 2 (high availability)
└─ PodDisruptionBudget created

MONITORING ✅
├─ Metrics collection (Prometheus)
├─ Dashboard (Grafana)
├─ Alerting configured
├─ Log collection (ELK/EFK)
└─ APM (optional but recommended)

SECURITY ✅
├─ RBAC configured
├─ Network policies configured
├─ Secrets management (not in code!)
├─ Container registry scanning
├─ Pod security policies
└─ Audit logging enabled

BACKUP & DISASTER RECOVERY ✅
├─ Regular backups (Velero)
├─ Recovery tested
├─ RTO/RPO defined
└─ Runbook documented

RESOURCE MANAGEMENT ✅
├─ Namespace quotas set
├─ Resource limits enforced
├─ HPA configured
├─ Cost monitoring in place
└─ Regular optimization review
```

---

## Part 5: Common Mistakes to Avoid ⚠️

### Mistake 1: No Resource Requests/Limits
```
❌ Pod definition:
   spec:
     containers:
     - image: myapp
       # No requests/limits!

Result:
├─ Pod uses all available memory
├─ Other pods can't run
└─ System unstable 💥

✅ Fixed:
   resources:
     requests:
       memory: "256Mi"
       cpu: "250m"
     limits:
       memory: "512Mi"
       cpu: "500m"
```

### Mistake 2: Single Replica in Production
```
❌ replicas: 1

Pod crashes → Downtime! 💥

✅ Fixed: replicas: 3 (minimum)
```

### Mistake 3: No Health Checks
```
❌ No health checks

Pod stuck in bad state
Kubernetes doesn't know
Requests fail silently

✅ Fixed: Add liveness + readiness probes
```

### Mistake 4: Storing Secrets in Code
```
❌ Dockerfile:
   ENV PASSWORD=mypassword123

Result: Exposed in image! 🔓

✅ Fixed:
   Use Kubernetes Secrets
   Mount as environment variables
```

### Mistake 5: No Ingress/LoadBalancer
```
❌ Users can't reach your service!
   Services are internal only

✅ Fixed: Create Ingress/LoadBalancer
```

---

## Part 6: Real-World Scenarios 🎯

### Scenario: Deploying Database 🗄️

```
Requirement: PostgreSQL with persistent storage

Solution:
1. Create PersistentVolumeClaim (50 GB)
2. Create StatefulSet (1 replica)
3. Configure health checks
4. Set resource limits (2 GB RAM)
5. Create backup CronJob
6. Test disaster recovery

Result:
├─ Data survives pod restarts ✅
├─ Stable network identity ✅
├─ Automatic backup ✅
└─ Ready for production ✅
```

### Scenario: Zero-Downtime Deployment 🔄

```
Current: 3 Order Service pods (v1)
Goal: Deploy v2 without downtime

Deployment strategy: Rolling Update

1. Create pod with v2
2. Wait for readiness probe ✅
3. Route traffic to v2 ✅
4. Kill v1 pod
5. Create pod with v2
6. Wait for readiness probe ✅
7. Route traffic to v2 ✅
8. Kill v1 pod
9. Create pod with v2
10. Wait for readiness probe ✅
11. Route traffic to v2 ✅
12. Kill last v1 pod

Result:
├─ Always 2+ pods running
├─ Always accepting requests ✅
├─ Zero downtime ✅
└─ Can rollback if needed ✅
```

### Scenario: Handling Traffic Spike 📈

```
Normal: 3 Order Service pods
Traffic spike: 10x increase!

Solution:
1. HPA detects CPU > 70%
2. Scale to 6 pods
3. Still high: Scale to 10 pods
4. Still high: Scale to 15 pods
5. CPU drops to 65% ✅
6. System stable ✅

Later: Traffic drops
7. Scale back to 3 pods ✅
8. Save costs! 💰
```

---

## Part 7: Kubernetes vs Alternatives 🏆

```
KUBERNETES
├─ Features: Complete, powerful
├─ Learning curve: STEEP 📈
├─ Cluster setup: COMPLEX
├─ Cloud integration: Best
├─ Use case: Enterprise, large scale
└─ Cost: Medium (self-hosted) or High (managed)

DOCKER COMPOSE
├─ Features: Basic (single machine)
├─ Learning curve: Easy
├─ Cluster setup: None needed
├─ Cloud integration: Limited
├─ Use case: Development, small deployments
└─ Cost: Free

HASHICORP NOMAD
├─ Features: Good (workload agnostic)
├─ Learning curve: Medium
├─ Cluster setup: Moderate
├─ Cloud integration: Good
├─ Use case: Multi-cloud, flexible
└─ Cost: Free (open source)

OPENSHIFT
├─ Features: Kubernetes + extras
├─ Learning curve: STEEP
├─ Cluster setup: COMPLEX
├─ Cloud integration: Red Hat focused
├─ Use case: Enterprise Red Hat shops
└─ Cost: High (licenses)

CHOOSE KUBERNETES IF:
✅ Need high availability
✅ Need auto-scaling
✅ Need multi-cloud
✅ Need declarative infrastructure
✅ Big team/enterprise
```

---

## Part 8: Kubernetes Monitoring & Observability 📊

### Three Pillars of Observability

**Metrics (Numbers):**
```
CPU: 45%
Memory: 256 MB
Requests: 1000/sec
Error rate: 0.5%
Latency: 50ms
```

**Logs (Events):**
```
2026-10-01 10:30:00 order-service started
2026-10-01 10:30:05 order-123 received
2026-10-01 10:30:06 payment-service called
2026-10-01 10:30:10 order-123 completed
```

**Traces (Requests):**
```
User request arrives
  ↓ 10ms
Order Service processes
  ↓ 15ms
Payment Service charges
  ↓ 20ms
Inventory Service updates
  ↓ 5ms
Response sent ✅
Total: 50ms
```

### Monitoring Stack

```
Prometheus: Collects metrics
    ↓
Grafana: Visualizes metrics
    ↓
AlertManager: Sends alerts
    ↓
On-call Engineer: Responds
```

---

## Summary: You're Now a Kubernetes Hero! 🦸

### What You Know:
✅ Pods, Deployments, Services
✅ Nodes, Cluster, Namespaces
✅ Ingress, StatefulSets, DaemonSets
✅ Jobs, CronJobs
✅ ConfigMaps, Secrets
✅ Volumes, PersistentVolumes
✅ Health checks (liveness/readiness)
✅ RBAC, Network Policies
✅ Resource requests/limits
✅ Taints & Tolerations
✅ Init containers
✅ Horizontal Pod Autoscaler (HPA)
✅ Pod Disruption Budgets

### What to Do Next:
1. **Set up locally** - Use minikube or Docker Desktop
2. **Experiment** - Deploy services, scale them
3. **Monitor** - Add Prometheus & Grafana
4. **Learn Helm** - Package manager for Kubernetes
5. **Learn Operators** - Advanced automation

---

*You've mastered Kubernetes! Ready for Helm and Operators next!* 🚀

---

*Last Updated: 2026-10-01*
*Organized for Complete Mastery! 🎯*
