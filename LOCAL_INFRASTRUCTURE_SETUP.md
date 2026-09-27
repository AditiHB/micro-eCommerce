# Local Infrastructure Setup Guide

Complete guide for setting up and running the entire microservices infrastructure locally with all dependencies.

---

## 📋 Table of Contents

1. [Prerequisites](#prerequisites)
2. [Infrastructure Components](#infrastructure-components)
3. [Quick Start](#quick-start)
4. [Detailed Configuration](#detailed-configuration)
5. [Service Health Checks](#service-health-checks)
6. [Accessing Services & Dashboards](#accessing-services--dashboards)
7. [Configuration Management](#configuration-management)
8. [Troubleshooting](#troubleshooting)
9. [Cleanup & Reset](#cleanup--reset)

---

## Prerequisites

### System Requirements

```bash
# Minimum Hardware
CPU: 4+ cores (for concurrent containers)
RAM: 16GB (8GB for OS + 8GB for containers)
Disk: 50GB free space (for images, volumes, databases)

# Check your system
system_profiler SPHardwareDataType  # macOS
lsb_release -a                      # Linux
```

### Required Software

```bash
# Docker (containerization)
docker --version                    # Should be 24.x+
docker-compose --version            # Should be 2.x+ (Docker Compose V2)

# Java Development Kit (for building from source)
java -version                       # Should be 21+

# Maven (build tool)
mvn --version                       # Should be 3.9+

# Git (version control)
git --version
```

### Installation Commands

**macOS (using Homebrew):**
```bash
# Install Docker Desktop (includes docker and docker-compose)
brew install --cask docker

# Or if using Colima lightweight container runtime:
brew install colima docker docker-compose

# Start Docker
colima start  # if using Colima
# or open Docker.app for Docker Desktop

# Verify installation
docker --version && docker-compose --version
```

**Linux (Ubuntu/Debian):**
```bash
# Install Docker
sudo apt-get update
sudo apt-get install -y docker.io docker-compose

# Add user to docker group (avoid sudo)
sudo usermod -aG docker $USER
newgrp docker

# Verify
docker --version && docker-compose --version
```

**Windows:**
```bash
# Install Docker Desktop for Windows
# From: https://www.docker.com/products/docker-desktop

# Or using Chocolatey:
choco install docker-desktop

# Verify in PowerShell
docker --version
docker-compose --version
```

---

## Infrastructure Components

The local infrastructure consists of **18 containerized services**:

### 1. Core Services (Microservices)
```
┌─────────────────────────────────────────┐
│         API Gateway (8080)              │
├─────────────────────────────────────────┤
│  ├─ Customer Service (8081)             │
│  ├─ Inventory Service (8082)            │
│  ├─ Order Service (8083)                │
│  └─ Payment Service (8084)              │
└─────────────────────────────────────────┘
```

### 2. Infrastructure Services
```
Config Server (8888)     - Centralized configuration
Discovery Server (8761)  - Eureka service discovery
```

### 3. Message Broker
```
Zookeeper (2181)    - Kafka coordination
Kafka (9092)        - Event streaming
```

### 4. Logging Stack (ELK)
```
Elasticsearch (9200) - Log storage & search
Logstash (50000)     - Log processing
Kibana (5601)        - Log visualization & analysis
```

### 5. Monitoring Stack (PMET)
```
Prometheus (9090)        - Metrics collection
Grafana (3000)           - Metrics visualization
Alertmanager (9093)      - Alert routing
Node Exporter (9100)     - System metrics
Elasticsearch Exporter   - Elasticsearch metrics
Loki (3100)              - Log aggregation
```

---

## Quick Start

### 1️⃣ Clone Repository
```bash
git clone https://github.com/CleanCoder007/micro-eCommerce.git
cd micro-eCommerce
```

### 2️⃣ Build All Services
```bash
# Option A: Build everything (includes tests)
mvn clean package

# Option B: Fast build (skip tests)
mvn clean package -DskipTests

# Expected output:
# [INFO] BUILD SUCCESS
# Total time: ~5-10 minutes
```

### 3️⃣ Start Infrastructure
```bash
# Start all services with Docker Compose
docker-compose up -d

# Verify all containers are running
docker-compose ps

# Expected output (all services should show "Up"):
# NAME                  STATUS
# api-gateway           Up 2 minutes
# customer-service      Up 2 minutes
# inventory-service     Up 2 minutes
# order-service         Up 2 minutes
# payment-service       Up 2 minutes
# kafka                 Up 2 minutes
# zookeeper             Up 2 minutes
# prometheus            Up 2 minutes
# grafana               Up 2 minutes
# elasticsearch         Up 2 minutes
# logstash              Up 2 minutes
# kibana                Up 2 minutes
# ... (more services)
```

### 4️⃣ Verify Services Are Running
```bash
# Wait 30-60 seconds for services to start

# Health check - API Gateway
curl http://localhost:8080/actuator/health

# Expected response:
# {"status":"UP","components":{"...":"..."}}
```

### 5️⃣ Access Web Interfaces

| Service | URL | Credentials |
|---------|-----|-------------|
| **API Gateway** | http://localhost:8080 | N/A |
| **Swagger UI** | http://localhost:8080/swagger-ui.html | N/A |
| **Grafana** | http://localhost:3000 | admin / admin123 |
| **Prometheus** | http://localhost:9090 | N/A |
| **Kibana** | http://localhost:5601 | N/A |
| **Alertmanager** | http://localhost:9093 | N/A |

### 6️⃣ Stop Services
```bash
# Stop all containers
docker-compose down

# Stop and remove volumes (clean state)
docker-compose down -v
```

---

## Detailed Configuration

### Configuration Files Structure

```
micro-eCommerce/
├── docker-compose.yml              # Main Docker Compose configuration
├── config-repo/                    # Spring Cloud Config repository
│   ├── application.yml             # Global config
│   ├── customer-service.yml
│   ├── inventory-service.yml
│   ├── order-service.yml
│   └── payment-service.yml
├── infrastructure/                 # Infrastructure services
│   ├── config-server/              # Config server source
│   ├── discovery-server/           # Eureka discovery server
│   ├── api-gateway/                # Gateway source
│   └── logstash/
│       └── pipeline/
│           └── logstash.conf       # Log processing config
└── monitoring/
    ├── prometheus.yml              # Prometheus config
    ├── alert-rules.yml             # Alert rules
    ├── alertmanager.yml            # AlertManager config
    ├── grafana-datasources.yml      # Grafana datasources
    ├── grafana-dashboards.yml       # Grafana dashboards
    ├── loki-config.yml              # Loki log aggregation
    └── grafana-dashboards/          # Dashboard definitions
```

### Service Configuration Details

#### 1. Database Configuration (H2 In-Memory)

Each service uses H2 in-memory database by default:

```yaml
# application.yml
spring:
  datasource:
    url: jdbc:h2:mem:service_db
    driverClassName: org.h2.Driver
    username: sa
    password: 
  h2:
    console:
      enabled: true
      path: /h2-console
  jpa:
    database-platform: org.hibernate.dialect.H2Dialect
```

**Access H2 Console:**
```
Customer Service:  http://localhost:8081/h2-console
Order Service:     http://localhost:8083/h2-console
Payment Service:   http://localhost:8084/h2-console
Inventory Service: http://localhost:8082/h2-console
```

#### 2. Kafka Configuration

```yaml
# Docker Compose environment
KAFKA_BROKER_ID: 1
KAFKA_ZOOKEEPER_CONNECT: zookeeper:2181
KAFKA_ADVERTISED_LISTENERS: PLAINTEXT://kafka:29092,PLAINTEXT_HOST://localhost:9092
```

**Kafka Topics Created Automatically:**
```
order.created
order.confirmed
order.cancelled
payment.processed
payment.failed
inventory.reserved
inventory.released
inventory.failed
customer.created
event.dlq
```

#### 3. Redis Cache Configuration (Optional)

If using Redis (not included in docker-compose by default):

```yaml
# application.yml
spring:
  redis:
    host: localhost
    port: 6379
    timeout: 2000
    password:
  cache:
    type: redis
    redis:
      time-to-live: 600000  # 10 minutes
```

Add to docker-compose.yml if needed:
```yaml
redis:
  image: redis:7-alpine
  container_name: redis
  ports:
    - "6379:6379"
```

#### 4. Elasticsearch Configuration

```yaml
# elasticsearch in docker-compose.yml
discovery.type: single-node
xpack.security.enabled: false
ES_JAVA_OPTS: -Xms512m -Xmx512m
```

#### 5. Prometheus Configuration

**monitoring/prometheus.yml:**
```yaml
global:
  scrape_interval: 15s
  evaluation_interval: 15s

scrape_configs:
  - job_name: 'api-gateway'
    static_configs:
      - targets: ['localhost:8080']
    metrics_path: '/actuator/prometheus'
  
  - job_name: 'customer-service'
    static_configs:
      - targets: ['localhost:8081']
    metrics_path: '/actuator/prometheus'
  
  # ... other services
```

#### 6. Grafana Datasources

**monitoring/grafana-datasources.yml:**
```yaml
apiVersion: 1
datasources:
  - name: Prometheus
    type: prometheus
    url: http://prometheus:9090
    access: proxy
    isDefault: true
  
  - name: Elasticsearch
    type: elasticsearch
    url: http://elasticsearch:9200
    access: proxy
```

---

## Service Health Checks

### 1. Check Running Containers

```bash
# View all running containers
docker-compose ps

# View container logs
docker-compose logs -f api-gateway

# View logs for specific service
docker-compose logs -f order-service --tail=50
```

### 2. Health Check Endpoints

```bash
# API Gateway Health
curl http://localhost:8080/actuator/health

# Detailed health info
curl http://localhost:8080/actuator/health/liveness
curl http://localhost:8080/actuator/health/readiness

# Customer Service Health
curl http://localhost:8081/actuator/health

# Order Service Health
curl http://localhost:8083/actuator/health

# Payment Service Health
curl http://localhost:8084/actuator/health

# Inventory Service Health
curl http://localhost:8082/actuator/health
```

### 3. Service Discovery (Eureka)

```bash
# View registered services
curl http://localhost:8761/eureka/apps

# View specific service instances
curl http://localhost:8761/eureka/apps/customer-service
```

### 4. Kafka Health

```bash
# Check Kafka topics
docker-compose exec kafka kafka-topics --list --bootstrap-server localhost:9092

# Check Kafka broker status
docker-compose exec zookeeper zookeeper-shell.sh localhost:2181 ls /brokers/ids
```

### 5. Elasticsearch Health

```bash
# Check Elasticsearch status
curl http://localhost:9200/_cluster/health

# View indices
curl http://localhost:9200/_cat/indices

# Expected response:
# {
#   "cluster_name": "docker-cluster",
#   "status": "green",
#   "number_of_nodes": 1
# }
```

---

## Accessing Services & Dashboards

### API Endpoints

#### Base URL
```
http://localhost:8080  (API Gateway)
```

#### Customer Service
```bash
# Create customer
curl -X POST http://localhost:8080/api/customers \
  -H "Content-Type: application/json" \
  -d '{
    "name": "John Doe",
    "email": "john@example.com"
  }'

# Get customers
curl http://localhost:8080/api/customers

# Get specific customer
curl http://localhost:8080/api/customers/1
```

#### Order Service
```bash
# Create order
curl -X POST http://localhost:8080/api/orders \
  -H "Content-Type: application/json" \
  -d '{
    "customerId": 1,
    "productId": "PROD-001",
    "quantity": 2
  }'

# Get orders
curl http://localhost:8080/api/orders
```

#### Inventory Service
```bash
# Get inventory
curl http://localhost:8080/api/inventory

# Get specific product inventory
curl http://localhost:8080/api/inventory/PROD-001
```

#### Payment Service
```bash
# Get payments
curl http://localhost:8080/api/payments

# Get specific payment
curl http://localhost:8080/api/payments/1
```

### Web Dashboards

#### 1. **Grafana** (Metrics Dashboard)
```
URL: http://localhost:3000
Username: admin
Password: admin123
```

**Actions:**
1. Login with admin/admin123
2. Go to Dashboards → Browse
3. Select "eCommerce Microservices" dashboard
4. View metrics like:
   - Orders per minute
   - Payment processing time
   - API response times
   - Error rates

#### 2. **Prometheus** (Metrics Storage)
```
URL: http://localhost:9090
```

**Actions:**
1. Go to Graph tab
2. Enter query examples:
   - `ecommerce_orders_created` - Total orders created
   - `rate(ecommerce_orders_created[5m])` - Orders per minute
   - `ecommerce_payment_processing_time` - Payment duration
   - `rate(http_requests_total[5m])` - Request rate

#### 3. **Kibana** (Log Analysis)
```
URL: http://localhost:5601
```

**Actions:**
1. Go to Discover
2. Create index pattern: `logs-*`
3. Search logs by:
   - Service name
   - Error messages
   - Request IDs
   - Timestamps

#### 4. **Swagger UI** (API Documentation)
```
URL: http://localhost:8080/swagger-ui.html
```

**Actions:**
1. Browse all API endpoints
2. Try out endpoints directly from UI
3. View request/response schemas

#### 5. **Alertmanager** (Alert Management)
```
URL: http://localhost:9093
```

**Actions:**
1. View active alerts
2. Manage silences
3. Group alerts by label

---

## Configuration Management

### 1. Spring Cloud Config Server

The Config Server centralizes configuration for all services:

**Port:** 8888

**Configuration Repository:** `./config-repo/`

**Add/Update Configuration:**

```bash
# Edit configuration files
vim config-repo/application.yml
vim config-repo/customer-service.yml

# Restart services to pick up changes
docker-compose restart api-gateway customer-service
```

**Configuration Hierarchy:**
1. `application.yml` - Global configuration
2. `{service-name}.yml` - Service-specific overrides
3. Environment variables - Runtime overrides

### 2. Environment Variables

Set environment variables in docker-compose.yml:

```yaml
environment:
  - SPRING_DATASOURCE_URL=jdbc:h2:mem:customer_db
  - SPRING_REDIS_HOST=localhost
  - SPRING_KAFKA_BOOTSTRAP_SERVERS=kafka:29092
  - LOGGING_LEVEL_ROOT=INFO
  - LOGGING_LEVEL_COM_ECOMMERCE=DEBUG
```

### 3. Application Properties Profiles

**Development Profile:**
```yaml
spring:
  profiles:
    active: dev
  datasource:
    url: jdbc:h2:mem:db
  jpa:
    hibernate:
      ddl-auto: create-drop
```

**Production Profile (reference):**
```yaml
spring:
  profiles:
    active: prod
  datasource:
    url: jdbc:postgresql://postgres:5432/order_db
  jpa:
    hibernate:
      ddl-auto: validate
```

---

## Troubleshooting

### Common Issues & Solutions

#### Issue 1: Containers Won't Start

**Symptom:**
```
docker-compose up
ERROR: error creating container
```

**Solutions:**
```bash
# Check disk space
df -h

# Free up Docker resources
docker system prune -a

# Check Docker daemon
docker info

# Increase Docker memory (Docker Desktop)
# Settings → Resources → Memory: 8GB+
```

#### Issue 2: Port Already in Use

**Symptom:**
```
Error: Bind for 0.0.0.0:8080 failed: port is already allocated
```

**Solutions:**
```bash
# Find process using port
lsof -i :8080

# Kill process
kill -9 <PID>

# Or use different port in docker-compose.yml
ports:
  - "8081:8080"  # Change host port to 8081
```

#### Issue 3: Services Not Starting Properly

**Symptom:**
```
docker-compose logs api-gateway
ERROR: Unable to connect to discovery server
```

**Solutions:**
```bash
# Check service dependencies
docker-compose ps

# Ensure config-server and discovery-server are running first
docker-compose up -d config-server discovery-server
sleep 10
docker-compose up -d api-gateway

# View detailed logs
docker-compose logs --tail=100 api-gateway
```

#### Issue 4: Kafka Not Processing Messages

**Symptom:**
```
Order created but payment not triggered
```

**Solutions:**
```bash
# Check Kafka topics
docker-compose exec kafka kafka-topics --list --bootstrap-server localhost:9092

# Check consumer groups
docker-compose exec kafka kafka-consumer-groups --list --bootstrap-server localhost:9092

# Check messages in topic
docker-compose exec kafka kafka-console-consumer --topic order-created \
  --from-beginning --bootstrap-server localhost:9092
```

#### Issue 5: High CPU/Memory Usage

**Symptom:**
```
Docker taking 100% CPU or memory
```

**Solutions:**
```bash
# Check container stats
docker stats

# Check Java heap usage
docker-compose exec order-service jps -l

# Reduce JVM heap size
environment:
  - JAVA_OPTS=-Xmx512m -Xms256m

# Restart containers
docker-compose restart
```

#### Issue 6: Elasticsearch Not Healthy

**Symptom:**
```
curl http://localhost:9200/_cluster/health
{"status":"red"}
```

**Solutions:**
```bash
# Check Elasticsearch logs
docker-compose logs elasticsearch

# Check disk space (Elasticsearch requires space)
df -h

# Restart Elasticsearch
docker-compose restart elasticsearch

# Reset Elasticsearch data
docker-compose down -v  # Remove volumes
docker-compose up -d elasticsearch
```

#### Issue 7: Grafana Dashboards Empty

**Symptom:**
```
Grafana shows "No data"
```

**Solutions:**
```bash
# Check Prometheus targets
curl http://localhost:9090/api/v1/targets

# Expected: All targets should show "UP"

# If DOWN, check service health:
curl http://localhost:8080/actuator/prometheus

# Reconfigure data source in Grafana:
# Configuration → Data Sources → Edit Prometheus
# Test data source - should show "Data source is working"
```

#### Issue 8: Build Failures

**Symptom:**
```
mvn clean package
[ERROR] BUILD FAILURE
```

**Solutions:**
```bash
# Check Java version
java -version  # Should be 21+

# Clean Maven cache
rm -rf ~/.m2/repository
mvn clean package

# Check Maven version
mvn -version  # Should be 3.9+

# Build specific module for debugging
mvn clean package -f services/order-service/pom.xml -X
```

---

## Cleanup & Reset

### Complete Cleanup

```bash
# Stop all containers
docker-compose down

# Stop and remove all volumes (database reset)
docker-compose down -v

# Remove all Docker images
docker-compose down -v --rmi all

# Clean Maven build artifacts
mvn clean

# Remove Docker system cache
docker system prune -a
```

### Partial Cleanup

```bash
# Stop specific service
docker-compose stop order-service

# Stop without removing
docker-compose pause order-service
docker-compose unpause order-service

# Remove specific container data
docker volume rm micro-ecommerce_prometheus-data
docker volume rm micro-ecommerce_grafana-data

# View all volumes
docker volume ls
```

### Reset to Fresh State

```bash
# Complete reset
docker-compose down -v --rmi all

# Rebuild without cache
docker-compose up -d --build --no-cache

# Rebuild and start from scratch
mvn clean package -DskipTests && docker-compose up -d --build
```

### Monitor Cleanup Progress

```bash
# View Docker resource usage
docker system df

# View volumes
docker volume ls

# View images
docker images

# View containers (including stopped)
docker container ls -a
```

---

## Performance Optimization

### Memory Optimization

**Reduce Resource Usage:**
```bash
# Edit docker-compose.yml
services:
  elasticsearch:
    environment:
      - ES_JAVA_OPTS=-Xms256m -Xmx256m  # Reduce from 512m

  prometheus:
    # Remove storage retention
    command:
      - '--storage.tsdb.retention.time=1d'  # Reduce from 15d
```

### Startup Optimization

**Faster Startup:**
```bash
# Increase max file descriptors (Linux)
ulimit -n 65536

# Pre-build images
docker-compose build

# Start only essential services
docker-compose up -d config-server discovery-server api-gateway kafka zookeeper
```

---

## Advanced Configuration

### Custom Network

```yaml
networks:
  microservices:
    driver: bridge
    ipam:
      config:
        - subnet: 172.20.0.0/16
```

### Volume Persistence

```yaml
volumes:
  postgres-data:
    driver: local
  redis-data:
    driver: local
```

### Logging Configuration

```yaml
logging:
  driver: "json-file"
  options:
    max-size: "10m"
    max-file: "3"
```

---

## Related Documentation

- **SETUP_AND_DEPLOYMENT.md** - Installation and deployment guide
- **ARCHITECTURE.md** - System architecture overview
- **SAGA_PATTERN_GUIDE.md** - Saga pattern with compensating transactions
- **DOCKER_COMPOSE_REFERENCE.md** - Docker Compose file reference (if available)

---

## Quick Reference Cheat Sheet

```bash
# Start infrastructure
docker-compose up -d

# View status
docker-compose ps

# View logs
docker-compose logs -f <service-name>

# Rebuild services
docker-compose up -d --build

# Stop services
docker-compose down

# Stop and clean volumes
docker-compose down -v

# Execute command in container
docker-compose exec <service> <command>

# Health check
curl http://localhost:8080/actuator/health

# API Gateway
http://localhost:8080

# Swagger UI
http://localhost:8080/swagger-ui.html

# Grafana
http://localhost:3000

# Prometheus
http://localhost:9090

# Kibana
http://localhost:5601

# Alertmanager
http://localhost:9093
```

---

**Last Updated**: September 27, 2026
**Status**: Production Ready
**Infrastructure Version**: 1.0
