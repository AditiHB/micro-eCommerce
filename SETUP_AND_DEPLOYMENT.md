# Complete Setup & Deployment Guide

## 📋 Table of Contents
1. [Prerequisites](#prerequisites)
2. [Local Development Setup](#local-development-setup)
3. [Running Services](#running-services)
4. [Docker Setup](#docker-setup)
5. [Kubernetes Deployment](#kubernetes-deployment)
6. [Monitoring Stack](#monitoring-stack)
7. [Testing](#testing)
8. [Troubleshooting](#troubleshooting)

---

## Prerequisites

### Required Software
```bash
# Java Development Kit
java -version                    # Should be 21+
# Verify: openjdk version "21.x.x"

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
- **CPU**: 4+ cores (for parallel builds and containers)
- **RAM**: 16GB minimum (8GB for VM + 8GB for services/database/redis)
- **Disk**: 50GB free (for Docker images, databases, logs)

### Network Ports
```
8080 - API Gateway (main entry point)
8081 - Customer Service
8082 - Inventory Service
8083 - Order Service
8084 - Payment Service
5432 - PostgreSQL (if used)
6379 - Redis
9092 - Kafka
3000 - Grafana
9090 - Prometheus
```

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
│   │   ├── config/                     # RedisConfig, CacheConfig
│   │   ├── metrics/                    # ApplicationMetrics, BusinessMetrics
│   │   ├── logging/                    # RequestResponseLoggingFilter
│   │   ├── dto/                        # Data Transfer Objects
│   │   ├── exception/                  # Custom exceptions
│   │   └── constants/                  # API constants
│   └── pom.xml                         # Parent POM (dependency management)
│
├── services/                            # Microservices
│   ├── customer-service/               # Customer CRUD
│   │   ├── src/main/java/...
│   │   └── src/main/resources/
│   │       └── application.yml         # Service config
│   ├── order-service/                  # Order management
│   ├── payment-service/                # Payment processing
│   └── inventory-service/              # Inventory management
│
├── api-gateway/                        # API Gateway (Spring Cloud Gateway)
│   ├── src/main/java/...
│   └── src/main/resources/
│       └── application.yml
│
├── k8s/                                # Kubernetes manifests
│   ├── namespace.yaml
│   ├── deployments/
│   ├── services/
│   └── statefulsets/
│
├── helm/                               # Helm charts
│   ├── Chart.yaml
│   ├── values.yaml
│   └── templates/
│
└── monitoring/                         # Prometheus, Grafana configs
    ├── prometheus.yml
    ├── alert-rules.yml
    └── grafana/dashboards/
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
cd services/api-gateway
mvn spring-boot:run

# ✓ Running at http://localhost:8080 (main entry point)
```

### Option 2: Run with Docker Compose

```bash
# Start all services + Redis + infrastructure
docker-compose up

# Output:
# ✓ customer-service | Started CustomerServiceApplication
# ✓ order-service | Started OrderServiceApplication
# ✓ payment-service | Started PaymentServiceApplication
# ✓ inventory-service | Started InventoryServiceApplication
# ✓ api-gateway | Started ApiGatewayApplication
# ✓ redis | * Ready to accept connections

# Access:
# - API Gateway: http://localhost:8080
# - Redis CLI: redis-cli
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
helm install micro-ecommerce ./helm \
  -n ecommerce \
  --values helm/values.yaml

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

```bash
# Apply all K8s manifests
kubectl apply -f k8s/namespace.yaml
kubectl apply -f k8s/configmaps/
kubectl apply -f k8s/secrets/
kubectl apply -f k8s/deployments/
kubectl apply -f k8s/services/
kubectl apply -f k8s/statefulsets/

# Verify
kubectl get all -n ecommerce
```

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

### Start Prometheus

```bash
# Option 1: Docker
docker run -d \
  --name prometheus \
  -p 9090:9090 \
  -v $(pwd)/monitoring/prometheus.yml:/etc/prometheus/prometheus.yml \
  prom/prometheus:latest

# Option 2: Kubernetes
kubectl apply -f monitoring/k8s/prometheus.yaml -n ecommerce
```

**Access Prometheus:**
- URL: http://localhost:9090
- Query examples:
  - `rate(ecommerce_orders_created[5m])` - Orders per second
  - `ecommerce_order_creation_time_seconds` - Order duration
  - `up{job="customer-service"}` - Service status

### Start Grafana

```bash
# Option 1: Docker
docker run -d \
  --name grafana \
  -p 3000:3000 \
  -e GF_SECURITY_ADMIN_PASSWORD=admin \
  grafana/grafana:latest

# Option 2: Kubernetes
kubectl apply -f monitoring/k8s/grafana.yaml -n ecommerce
```

**Access Grafana:**
- URL: http://localhost:3000
- Username: admin
- Password: admin (change immediately!)

**Setup Data Source:**
1. Configuration → Data Sources
2. Add Prometheus
3. URL: http://localhost:9090 (or prometheus:9090 in K8s)
4. Save & Test

**Import Dashboards:**
1. Dashboards → Browse
2. Import → Paste JSON from `monitoring/grafana/dashboards/`
3. Select Prometheus data source
4. Save

### Start Alertmanager

```bash
# Option 1: Docker
docker run -d \
  --name alertmanager \
  -p 9093:9093 \
  -v $(pwd)/monitoring/alertmanager.yml:/etc/alertmanager/alertmanager.yml \
  prom/alertmanager:latest

# Option 2: Kubernetes
kubectl apply -f monitoring/k8s/alertmanager.yaml -n ecommerce
```

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

### API Testing with Postman
```bash
# Import collection
1. Open Postman
2. Import → postman-collection.json
3. Select environment
4. Run collection
```

### Load Testing with JMeter
```bash
# Run load test
jmeter -n -t tests/load/order_api.jmx -l results.jtl

# Open results
jmeter -g results.jtl
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

# PostgreSQL
psql -U postgres
DROP DATABASE order_db;
CREATE DATABASE order_db;

# Then restart service (Liquibase auto-creates schema)
```

#### Check Migrations
```bash
# View Liquibase history
mvn liquibase:history

# Rollback last migration
mvn liquibase:rollback -Dliquibase.rollbackCount=1
```

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
4. **Read Architecture**: See `ARCHITECTURE.md`
5. **Learn Concepts**: See `CONCEPTS_EXPLAINED.md`
6. **Check Phases**: See `PHASES_GUIDE.md`

