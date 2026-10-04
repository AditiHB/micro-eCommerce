# Complete Setup & Deployment Guide

## 📋 Table of Contents
1. [Prerequisites](#prerequisites)
2. [Local Setup Scenarios](#local-setup-scenarios)
3. [Local Development Setup](#local-development-setup)
4. [Running Services](#running-services)
5. [Docker Setup](#docker-setup)
6. [Kubernetes Deployment](#kubernetes-deployment)
7. [Monitoring Stack](#monitoring-stack)
8. [Testing](#testing)
9. [Troubleshooting](#troubleshooting)

This project is **local-only**: everything here runs on your own machine, either with Docker Compose (one `docker-compose.yml` at the repo root, switched between scenarios with `--profile` flags) or inside a local Kubernetes cluster (minikube/kind) using the manifests under `k8s/`. There is no separate "production" compose file or environment to maintain.

---

## Prerequisites

### Required Software
```bash
# Java Development Kit
java -version                    # Should be 17+
# Verify: openjdk version "17.x.x"

# Maven (build tool)
mvn -version                     # Should be 3.9+

# Docker (containerization)
docker --version                 # Should be 24.x+

# Docker Compose (for local infrastructure)
docker-compose --version         # Should be 2.x+

# Kubernetes CLI (for K8s)
kubectl version                  # Should be 1.27+

# Helm (K8s package manager)
helm version                      # Should be 3.x+

# Git (version control)
git --version
```

### Hardware Requirements
- **CPU**: 2+ cores (4+ recommended for building all modules in parallel)
- **RAM**: the default core stack (H2, no observability) fits comfortably in **12GB** laptops - see the memory budget table in [Local Setup Scenarios](#local-setup-scenarios) below. Every container has an explicit `mem_limit` and every JVM service an explicit heap cap (`JAVA_TOOL_OPTIONS`/`*_HEAP_OPTS`) in `docker-compose.yml`, so usage is capped and predictable rather than growing unbounded.
- **Disk**: ~10GB free for Docker images (more if you also enable the `observability` profile - ELK images are large)

### Network Ports
```
8080 - API Gateway (main entry point)
8081 - Customer Service
8082 - Inventory Service
8083 - Order Service
8084 - Payment Service
8086 - Notification Service
8888 - Config Server
8761 - Eureka Dashboard
5432 - PostgreSQL (--profile postgres)
6379 - Redis
9092 - Kafka
2181 - Zookeeper
80/443 - Nginx HTTP/HTTPS (--profile https)
3000 - Grafana (--profile observability)
9090 - Prometheus (--profile observability)
5601 - Kibana (--profile observability)
```

---

## Local Setup Scenarios

Everything below uses the **single** `docker-compose.yml` at the repo root. Nothing is started that you didn't ask for - the default command starts only the core application stack, and every extra piece (Postgres, HTTPS, observability) is opt-in via `--profile`. Profiles can be combined freely.

| # | Scenario | Command | Adds |
|---|---|---|---|
| 1 | **Only H2** (default) | `docker compose up -d` | Core app stack only, each service with its own in-memory H2 database - nothing to configure |
| 2 | **Real PostgreSQL** | `docker compose --profile postgres --env-file .env.postgres up -d` | A `postgres` container + switches customer/inventory/order/payment/notification-service from H2 to Postgres (one schema per service, Flyway migrates automatically on startup - see [db/README.md](../db/README.md)) |
| 3 | **HTTPS locally** | `docker compose --profile https up -d` | An `nginx` container terminating TLS with a self-signed cert and forwarding `/api/` to the gateway. **Generate the cert first** (see [HTTPS setup](#https-setup) below) |
| 4 | **Compact / low-memory** (no logs, no ELK, no metrics) | `docker compose up -d` | Same as #1 - the observability stack is never started unless you explicitly request it. This is the lowest-memory option and the right default for resource-constrained machines |
| 5 | **Entire stack** | `docker compose --profile postgres --profile https --profile observability --env-file .env.postgres up -d` | Everything: Postgres + HTTPS + ELK/Prometheus/Grafana/Alertmanager/Loki |

Any subset of profiles can be combined, e.g. just HTTPS + Postgres without observability:
```bash
docker compose --profile postgres --profile https --env-file .env.postgres up -d
```

### Memory budget per scenario

Every container in `docker-compose.yml` has a `mem_limit`/`mem_reservation`, and every JVM-based one also caps its heap explicitly (so the JVM can't grow into the rest of the container and get OOM-killed by the kernel instead of GC'ing cleanly). Approximate totals (sum of `mem_limit` per profile combination):

| Profiles active | Approx. RAM | Notes |
|---|---|---|
| *(none)* - core stack, H2 | **~3.8 GB** | Zookeeper, Kafka, Redis, Config Server, Discovery Server, API Gateway, 5 microservices. Fits a 12GB/i3 laptop with plenty of headroom for the OS, Docker Desktop/WSL2, and your IDE. |
| `postgres` | +~320 MB | One extra lightweight Postgres container |
| `https` | +~64 MB | Nginx is tiny |
| `observability` | **+~3 GB** | ELK (Elasticsearch/Logstash/Kibana) + Prometheus/Grafana/Alertmanager/exporters/Loki - nearly as expensive as the whole application. Only start this when you actually want to browse logs in Kibana or dashboards in Grafana |
| All profiles (full stack) | **~7.2 GB** | Still comfortable headroom on a 12GB machine |

### HTTPS setup

The `https` profile expects certificates under `infrastructure/nginx/certs/`. Example certs are checked in for convenience, but to generate your own locally:
```bash
bash infrastructure/scripts/setup-certificates.sh
# Writes privkey.pem / cert.pem / fullchain.pem / chain.pem / dhparam.pem / session_ticket.key
# into infrastructure/nginx/certs/ (self-signed, valid for localhost / 127.0.0.1)
docker compose --profile https up -d
curl -k https://localhost/health          # nginx health check
curl -k https://localhost/api/customers   # through nginx -> gateway (401 without a token is expected)
```
Browsers and `curl` will warn about the self-signed cert - that's expected for local HTTPS; use `-k`/`--insecure` with curl, or accept the browser warning, or disable SSL verification in Postman/Insomnia (see `postman_environment_https.json`).

### Switching back to H2

Postgres is opt-in, not persistent-by-default: stop the stack and omit `--profile postgres --env-file .env.postgres` on your next `docker compose up -d` to go back to the default in-memory H2 (each service's data is freshly seeded on every restart either way, per [db/README.md](../db/README.md)).

---

## Local Development Setup

### 1. Clone Repository
```bash
git clone https://github.com/CleanCoder007/micro-eCommerce.git
cd micro-eCommerce
```

### 2. Build with Maven
```bash
# Clean build with all tests
mvn clean package

# Build skipping tests (faster)
mvn clean package -DskipTests

# Expected output:
# ✓ common module built
# ✓ customer-service built
# ✓ order-service built
# ✓ payment-service built
# ✓ inventory-service built
# Total: ~5 minutes (first time with downloads)
```

### 3. Project Structure Understanding
```
micro-eCommerce/
├── common/                              # Shared dependencies
│   ├── src/main/java/com/ecommerce/common/
│   │   ├── config/                     # RedisConfig, SecurityConfig
│   │   ├── metrics/                    # ApplicationMetrics, BusinessMetrics
│   │   ├── logging/                    # RequestResponseLoggingFilter
│   │   ├── dto/                        # Data Transfer Objects
│   │   ├── exception/                  # Custom exceptions
│   │   └── security/                   # JwtAuthenticationFilter, SecurityConfig
│   └── pom.xml                         # Parent POM (dependency management)
│
├── services/                            # Microservices
│   ├── customer-service/               # Customer CRUD
│   │   ├── src/main/java/...
│   │   └── src/main/resources/
│   │       └── application.yml         # Service config
│   ├── order-service/                  # Order management
│   ├── payment-service/                # Payment processing
│   ├── inventory-service/              # Inventory management
│   ├── notification-service/           # Customer/order notifications
│   └── product-service/                # Product catalog
│
├── infrastructure/                     # Infra services + ops config
│   ├── api-gateway/                    # Spring Cloud Gateway
│   ├── config-server/                  # Spring Cloud Config Server
│   ├── discovery-server/               # Netflix Eureka
│   ├── nginx/                          # HTTPS termination (--profile https)
│   ├── scripts/                        # Cert generation, secret rotation
│   └── postgres/                       # Multi-DB init script (--profile postgres)
│
├── k8s/                                 # Kubernetes manifests (flat, numbered files)
│
├── helm/ecommerce/                      # Helm chart
│   ├── Chart.yaml
│   ├── values.yaml
│   └── templates/
│
└── monitoring/                          # Prometheus, Grafana, Alertmanager, Loki configs
    ├── prometheus.yml
    ├── alert-rules.yml
    └── grafana-dashboards/
```

---

## Running Services

### Option 1: Run Each Service Individually

#### Terminal 1 - Customer Service
```bash
cd services/customer-service
mvn spring-boot:run

# Expected output:
# ... c.e.c.CustomerServiceApplication     : Started CustomerServiceApplication
# ... Tomcat started on port(s): 8081
# ✓ Service running at http://localhost:8081
```

#### Terminal 2 - Order Service
```bash
cd services/order-service
mvn spring-boot:run

# ✓ Running at http://localhost:8083
```

#### Terminal 3 - Inventory Service
```bash
cd services/inventory-service
mvn spring-boot:run

# ✓ Running at http://localhost:8082
```

#### Terminal 4 - Payment Service
```bash
cd services/payment-service
mvn spring-boot:run

# ✓ Running at http://localhost:8084
```

#### Terminal 5 - API Gateway
```bash
cd infrastructure/api-gateway
mvn spring-boot:run

# ✓ Running at http://localhost:8080 (main entry point)
```

### Option 2: Run with Docker Compose

See [Local Setup Scenarios](#local-setup-scenarios) above for the full set of options (H2, Postgres, HTTPS, observability). The simplest case:

```bash
# Start the core stack (H2, no extra flags needed)
docker compose up -d

# Access:
# - API Gateway: http://localhost:8080
# - Eureka Dashboard: http://localhost:8761
```

### Option 3: Run Specific Service in IDE

**Using IntelliJ IDEA:**
1. Open Run → Run Configurations
2. Create new Spring Boot application
3. Main class: `com.ecommerce.customerservice.CustomerServiceApplication`
4. Working directory: `$PROJECT_DIR/services/customer-service`
5. Click Run

---

## Docker Setup

### Build Docker Images

#### Build Single Service Image
```bash
cd services/customer-service
docker build -t customer-service:latest .

# Expected output:
# Sending build context to Docker daemon  50MB
# Step 1/12 : FROM maven:3.9 AS builder
# ...
# Successfully tagged customer-service:latest
```

#### Build All Services
```bash
# Script to build all services
for service in customer-service order-service payment-service inventory-service api-gateway; do
  echo "Building $service..."
  cd services/$service
  docker build -t $service:latest .
  cd ../..
done
```

#### Push to Registry (Optional)
```bash
# Tag images for registry
docker tag customer-service:latest myregistry.azurecr.io/customer-service:v1.0

# Push to registry
docker push myregistry.azurecr.io/customer-service:v1.0
```

### Run Service in Docker
```bash
# Prerequisites: Redis running
docker run -d \
  --name customer-service \
  --network host \
  -e SPRING_DATASOURCE_URL=jdbc:h2:mem:customer_db \
  -e SPRING_REDIS_HOST=localhost \
  -e SPRING_REDIS_PORT=6379 \
  -p 8081:8081 \
  customer-service:latest

# Verify:
docker ps              # List running containers
docker logs customer-service   # View logs
curl http://localhost:8081/api/customers  # Test API
```

---

## Kubernetes Deployment

### Prerequisites
```bash
# Start Kubernetes cluster
minikube start --cpus=4 --memory=8192 --disk-size=50g

# Verify cluster is running
kubectl cluster-info
kubectl get nodes

# Expected output:
# NAME       STATUS   ROLES           AGE     VERSION
# minikube   Ready    control-plane   5m      v1.27.0
```

### Deploy Using Helm

```bash
# Create ecommerce namespace
kubectl create namespace ecommerce

# Install chart
helm install micro-ecommerce ./helm/ecommerce \
  -n ecommerce \
  --values helm/ecommerce/values.yaml

# Verify installation
kubectl get all -n ecommerce
kubectl get pods -n ecommerce -w

# Expected output (after 1-2 minutes):
# NAME                                 READY   STATUS    RESTARTS   AGE
# pod/customer-service-6c9d8f4c9-xxxx  1/1     Running   0          30s
# pod/order-service-6c9d8f4c9-xxxx     1/1     Running   0          25s
# pod/payment-service-6c9d8f4c9-xxxx   1/1     Running   0          20s
# pod/inventory-service-6c9d8f4c9-xxxx 1/1     Running   0          15s
# pod/api-gateway-6c9d8f4c9-xxxx       1/1     Running   0          10s
```

### Access Services

#### Through API Gateway (LoadBalancer)
```bash
# Get LoadBalancer external IP
kubectl get service api-gateway -n ecommerce

# Wait for EXTERNAL-IP to be assigned (minikube: use minikube service)
minikube service api-gateway -n ecommerce --url

# Test API
curl http://$(minikube ip):8080/api/customers
```

#### Port Forwarding (if no LoadBalancer)
```bash
# Forward local port to service
kubectl port-forward -n ecommerce \
  svc/api-gateway 8080:8080

# Now access at http://localhost:8080
```

### Deploy Using kubectl (Manual)

The manifests under `k8s/` are flat, numbered files applied in order (numbering encodes the dependency order - namespace/secrets first, then infra, then each service):

```bash
kubectl apply -f k8s/
# or apply in explicit order if you want to watch each step:
kubectl apply -f k8s/00-namespace.yaml
kubectl apply -f k8s/01-secrets.yaml -f k8s/02-configmaps.yaml
kubectl apply -f k8s/03-infrastructure.yaml
kubectl apply -f k8s/04-discovery-server.yaml -f k8s/05-config-server.yaml -f k8s/06-api-gateway.yaml
kubectl apply -f k8s/07-customer-service.yaml -f k8s/08-order-service.yaml -f k8s/09-inventory-service.yaml -f k8s/10-payment-service.yaml -f k8s/18-notification-service.yaml

# Verify
kubectl get all -n ecommerce
```
See [docs/KUBERNETES_DEPLOYMENT.md](KUBERNETES_DEPLOYMENT.md) for the full manifest list (mTLS, network policies, secrets rotation, etc.) and [docs/SECRETS_MANAGEMENT.md](SECRETS_MANAGEMENT.md) for how secrets are provisioned.

### Monitor Deployment
```bash
# Watch pods come up
kubectl get pods -n ecommerce -w

# Check pod logs
kubectl logs -n ecommerce -l app=customer-service

# Get pod details
kubectl describe pod customer-service-xxxx -n ecommerce

# Execute command in pod
kubectl exec -n ecommerce -it customer-service-xxxx -- bash
```

### Update Deployment
```bash
# Update image version
kubectl set image deployment/customer-service \
  -n ecommerce \
  customer-service=customer-service:v2.0

# Rollout status
kubectl rollout status deployment/customer-service -n ecommerce

# Rollback if needed
kubectl rollout undo deployment/customer-service -n ecommerce
```

---

## Monitoring Stack

### Local (recommended): the `observability` Compose profile

The repo's own Prometheus/Grafana/Alertmanager config (`monitoring/prometheus.yml`, `monitoring/alert-rules.yml`, `monitoring/grafana-*`) is already wired up and pre-provisioned with dashboards for this stack - no manual data-source setup needed:

```bash
docker compose --profile observability up -d
```

**Access:**
- Prometheus: http://localhost:9090 (targets should show the 5 app services as `UP`)
- Grafana: http://localhost:3000 (admin / admin123, dashboards auto-provisioned from `monitoring/grafana-dashboards/`)
- Alertmanager: http://localhost:9093
- Kibana (logs): http://localhost:5601

Query examples in Prometheus:
- `rate(ecommerce_orders_created[5m])` - Orders per second
- `ecommerce_order_creation_time_seconds` - Order duration
- `up{job="customer-service"}` - Service status

### Kubernetes

The `k8s/` manifests in this repo focus on the application services, mTLS and secrets (see [docs/KUBERNETES_DEPLOYMENT.md](KUBERNETES_DEPLOYMENT.md)); they don't currently include Prometheus/Grafana. To monitor a K8s deployment, install the [kube-prometheus-stack Helm chart](https://github.com/prometheus-community/helm-charts/tree/main/charts/kube-prometheus-stack) into the `ecommerce` namespace, or point an existing cluster-wide Prometheus at the services' `/actuator/prometheus` endpoints.

---

## Testing

### Unit Tests
```bash
# Run tests in specific service
cd services/customer-service
mvn test

# Run specific test class
mvn test -Dtest=CustomerServiceTest

# Run with coverage
mvn test jacoco:report

# View coverage report
open target/site/jacoco/index.html
```

### Integration Tests
```bash
# Run all tests (unit + integration)
mvn verify

# Run only integration tests
mvn test -Dgroups=integration

# Run with test database
mvn test -Dspring.profiles.active=test
```

### End-to-end testing with Karate

A full customer-journey E2E scenario (login -> create customer -> browse
catalogue -> order -> pay -> notification) runs against the live stack over
real HTTP. See [e2e-tests/README.md](../e2e-tests/README.md).
```bash
docker compose up -d
mvn -f e2e-tests/pom.xml test -Dtest=CustomerJourneyRunner
```

### API Testing with Postman / Insomnia
```bash
# Import collection + matching environment
1. Open Postman (or Insomnia)
2. Import → postman-collection.json
3. Import → postman_environment_http.json (default) or postman_environment_https.json (--profile https)
4. Select the environment, then run requests/the collection
```

### HTTPS / mTLS test scripts
```bash
# Verify the nginx HTTPS setup (--profile https) end to end
bash infrastructure/tests/test-ssl.sh

# Verify mTLS between services (Kubernetes deployment - see docs/MTLS_CONFIGURATION.md)
bash infrastructure/tests/test-mtls.sh
```

### Health Checks
```bash
# Check individual service health
curl http://localhost:8081/actuator/health
curl http://localhost:8083/actuator/health/liveness
curl http://localhost:8083/actuator/health/readiness

# In Kubernetes
kubectl get pods -n ecommerce
# READY column shows 1/1 when liveness + readiness probes pass
```

---

## Troubleshooting

### Service Won't Start

#### Problem: Port Already in Use
```bash
# Find process using port
lsof -i :8081

# Kill process
kill -9 <PID>

# Or use different port
export SERVER_PORT=9081
mvn spring-boot:run
```

#### Problem: Database Connection Failed
```bash
# Check H2 in-memory database (dev)
# H2 console should be at http://localhost:8081/h2-console

# Check PostgreSQL (prod)
psql -h localhost -U postgres -d order_db
```

#### Problem: Redis Connection Refused
```bash
# Start Redis
docker run -d -p 6379:6379 redis:latest

# Or install locally
brew install redis      # macOS
redis-server            # Start server
```

### Service High CPU/Memory Usage

#### Check Resource Usage
```bash
# Docker
docker stats

# Kubernetes
kubectl top pods -n ecommerce
```

#### Solutions
```bash
# Check logs for errors
docker logs customer-service

# Increase heap size
export JAVA_OPTS="-Xmx1g -Xms512m"

# Update K8s resources
kubectl set resources deployment customer-service \
  -n ecommerce \
  --limits=cpu=1000m,memory=1Gi \
  --requests=cpu=500m,memory=512Mi
```

### API Returns 5xx Errors

#### Check Service Logs
```bash
# Docker
docker logs customer-service 2>&1 | tail -50

# Kubernetes
kubectl logs -n ecommerce deployment/customer-service --tail=50

# Follow logs in real-time
kubectl logs -n ecommerce -f deployment/customer-service
```

#### Common Errors
```
"Error creating bean": Check dependencies in pom.xml
"Datasource URL invalid": Check application.yml
"Unable to acquire JDBC Connection": Database not running
"Connection refused": Check if service port is correct
"JWT expired": Check system time synchronization
"Rate limit exceeded": Check rate limiting configuration
```

### Database Schema Issues

#### Reset Database (Development)
```bash
# H2 in-memory (auto-resets)
# Just restart service

# PostgreSQL (container started via --profile postgres)
docker exec -it postgres psql -U ecommerce_user -d order_db
DROP SCHEMA public CASCADE;
CREATE SCHEMA public;

# Then restart the service (Flyway auto-creates the schema on startup)
```

#### Check Migrations
```bash
# View applied/pending migrations for a service
mvn -pl services/order-service flyway:info
```
Flyway Community Edition has no automated rollback - revert by writing a new forward migration instead (see [../db/README.md](../db/README.md)).

### Network Issues in Kubernetes

#### Services Can't Communicate
```bash
# Check DNS
kubectl run -it --rm debug --image=busybox --restart=Never -- nslookup customer-service

# Expected: IPs for customer-service

# Check network policies
kubectl get networkpolicies -n ecommerce

# Test connectivity
kubectl exec -it pod/order-service -- \
  curl -v http://customer-service:8081/api/customers
```

#### External Access Issues
```bash
# Check ingress
kubectl get ingress -n ecommerce

# Check service endpoints
kubectl get endpoints -n ecommerce

# Port forward for debugging
kubectl port-forward svc/api-gateway 8080:8080 -n ecommerce
```

### Monitoring Stack Issues

#### Prometheus Not Scraping Metrics
```bash
# Check targets
curl http://localhost:9090/api/v1/targets

# Expected: All targets showing "UP"

# If DOWN, check:
1. Service is running
2. /actuator/prometheus endpoint accessible
3. Firewall rules allowing access
4. Service discovery configuration
```

#### Grafana Dashboards Empty
```bash
# Check data source
1. Go to Configuration → Data Sources
2. Test data source
3. Expected: "Data source is working"

# If failed:
1. Check Prometheus is running
2. Check network connectivity
3. Check Prometheus URL in Grafana
```

---

## Performance Optimization Tips

### 1. Cache Warm-up
```bash
# Pre-load frequently accessed data
curl http://localhost:8080/api/customers/popular
curl http://localhost:8080/api/products/trending
```

### 2. Connection Pooling
```yaml
# In application.yml
spring:
  datasource:
    hikari:
      maximum-pool-size: 20           # Increase if connections exhausted
      minimum-idle: 5
      connection-timeout: 20000
```

### 3. Redis Optimization
```bash
# Monitor Redis
redis-cli
> MONITOR                # Watch commands
> INFO stats             # View statistics
> DBSIZE                 # Flushed keys
```

### 4. JVM Tuning
```bash
# Set JVM options
export JAVA_OPTS="
  -Xmx2g                 # Max heap
  -Xms1g                 # Initial heap
  -XX:+UseG1GC           # Use G1 garbage collector
  -XX:MaxGCPauseMillis=200
"
```

---

## Next Steps

1. **Explore API Documentation**: http://localhost:8080/swagger-ui.html
2. **View Metrics**: http://localhost:9090 (Prometheus)
3. **Monitor Dashboards**: http://localhost:3000 (Grafana)
4. **Read Architecture**: See [ARCHITECTURE.md](ARCHITECTURE.md)
5. **Learn Concepts**: See [CONCEPTS_EXPLAINED.md](CONCEPTS_EXPLAINED.md)
6. **Check Phases**: See [PHASES_GUIDE.md](PHASES_GUIDE.md)

