# Docker in This Project 🐳
## Understanding Docker Usage in Your micro-eCommerce Architecture

---

## 📊 Project Architecture Overview

Your e-commerce project uses Docker in a **sophisticated multi-service setup** with:
- **5 Microservices** (Customer, Order, Payment, Inventory, Product)
- **3 Infrastructure Services** (API Gateway, Config Server, Discovery Server)
- **8 Supporting Services** (Kafka, Zookeeper, Elasticsearch, Kibana, Prometheus, Grafana, AlertManager, Loki)

**Total: 16 containers running together!**

---

## 🏗️ How Containers Work in Your Project

### The Traditional Way (Without Docker)
```
Your Laptop
├── Java 17 installed
├── Maven 3.8 installed
├── PostgreSQL running (port 5432)
├── Kafka running (port 9092)
├── Zookeeper running (port 2181)
├── Elasticsearch running (port 9200)
├── 5 Spring Boot apps running on different ports (8081-8085)
├── Plus Prometheus, Grafana, etc.
└── Problem: Takes HOURS to set up, breaks easily, not reproducible
```

### The Docker Way (Your Project)
```
docker-compose.yml (one file!)
└── docker-compose up -d
    └── All 16 services start automatically ✨
        ├── Kafka container
        ├── Customer Service container
        ├── Order Service container
        ├── Payment Service container
        ├── Inventory Service container
        ├── API Gateway container
        ├── Discovery Server container
        ├── Config Server container
        ├── Elasticsearch container
        ├── Kibana container
        ├── Prometheus container
        ├── Grafana container
        ├── Alertmanager container
        ├── Loki container
        ├── Logstash container
        ├── Zookeeper container
        └── All networking configured automatically!
```

**Result:** One command, entire system running! ⚡

---

## 🔍 Deep Dive: Your Microservices

### 1. Customer Service - Detailed Breakdown

#### Dockerfile.customer-service
```dockerfile
# Stage 1: Build (creates the application)
FROM maven:3.8-openjdk-17 as builder
WORKDIR /build

# Copy project files
COPY pom.xml .
COPY common ./common
COPY services/pom.xml ./services/
COPY services/customer-service ./services/customer-service/
COPY infrastructure/pom.xml ./infrastructure/

# Compile and package
RUN mvn clean package -DskipTests -pl services/customer-service -am

# Stage 2: Runtime (only includes what's needed to run)
FROM openjdk:17-jre-slim

# Install curl for health checks
RUN apt-get update && apt-get install -y curl && rm -rf /var/lib/apt/lists/*

WORKDIR /app

# Copy only the compiled JAR (not source code, much smaller!)
COPY --from=builder /build/services/customer-service/target/customer-service-*.jar app.jar

EXPOSE 8081

# Set port as environment variable
ENV SERVER_PORT=8081

# Health check - Docker checks this every 10 seconds
HEALTHCHECK --interval=10s --timeout=5s --start-period=30s --retries=3 \
  CMD curl -f http://localhost:8081/actuator/health/readiness || exit 1

# Start the application
ENTRYPOINT ["java", "-jar", "app.jar"]
```

#### What This Dockerfile Does - Line by Line

| Line | What It Does | Why |
|------|-------------|-----|
| `FROM maven:3.8-openjdk-17 as builder` | Start with Maven + Java image | Need Maven to compile code |
| `WORKDIR /build` | Create `/build` folder inside container | Organize files |
| `COPY pom.xml .` | Copy Maven config | Need it to download dependencies |
| `RUN mvn clean package...` | Compile and package the app | Creates `.jar` file |
| `FROM openjdk:17-jre-slim` | Start fresh with only Java runtime | Don't need Maven in final image, smaller size |
| `RUN apt-get install curl` | Install curl tool | Used for health checks |
| `COPY --from=builder ...jar` | Copy compiled JAR from builder stage | Don't include source code or Maven |
| `EXPOSE 8081` | Tell Docker app uses port 8081 | For documentation and port mapping |
| `ENV SERVER_PORT=8081` | Set port environment variable | Spring Boot reads this |
| `HEALTHCHECK ...curl...` | Check if app is healthy every 10 seconds | Docker automatically restarts if unhealthy |
| `ENTRYPOINT ["java", "-jar"...` | How to start the app | Execute Java JAR file |

#### Key Insight: Multi-Stage Build
```
Stage 1: Maven image (LARGE - ~500MB)
└── Compiles code
    └── Creates small customer-service-1.0.jar (15MB)

Stage 2: JRE image (SMALL - ~180MB)
└── Only gets the 15MB jar
    └── Final image: ~200MB (not 500MB!)
```

**This technique saves tons of disk space!** 🎯

---

### 2. Docker Compose Configuration Explained

#### Your docker-compose.yml Structure

```yaml
version: '3.8'                    # Compose file format version
services:                         # Define all containers

  zookeeper:                     # Service 1: Kafka dependency
    image: confluentinc/cp-zookeeper:7.3.2
    # Using pre-built image from Docker Hub
    
    container_name: zookeeper    # Name this container "zookeeper"
    
    environment:                 # Environment variables for container
      ZOOKEEPER_CLIENT_PORT: 2181
      ZOOKEEPER_TICK_TIME: 2000
    
    ports:                       # Port mapping
      - "2181:2181"             # Container port 2181 → Host port 2181
```

#### Port Mapping Explained
```
"8081:8081" means:
┌─────────────────────────────────────────────────┐
│                 Your Computer                    │
│              localhost:8081                      │
└────────────────┬─────────────────────────────────┘
                 │ Network connection
                 │
┌────────────────▼─────────────────────────────────┐
│            Container Network                     │
│   Port 8081 (Customer Service)                  │
└─────────────────────────────────────────────────┘
```

---

### 3. Service Dependencies (Startup Order)

#### Example: Order Service
```yaml
order-service:
  build:
    context: ./services/order-service    # Build from Dockerfile
  container_name: order-service
  ports:
    - "8083:8083"
  
  depends_on:                            # These MUST start first
    - config-server                      # Needs config from here
    - discovery-server                   # Registers itself here
    - kafka                              # Publishes events here
  
  environment:                           # Configuration
    - SPRING_CONFIG_IMPORT=optional:configserver:http://config-server:8888/
    # Tells Order Service where to find config server
    
    - EUREKA_CLIENT_SERVICEURL_DEFAULTZONE=http://discovery-server:8761/eureka/
    # Tells Order Service where to register itself
    
    - SPRING_KAFKA_BOOTSTRAP_SERVERS=kafka:29092
    # Tells Order Service where Kafka is
```

**This is powerful because:**
- Service names (`config-server`, `discovery-server`, `kafka`) become DNS names
- Services find each other automatically using internal Docker network
- No need to hardcode IP addresses! ✨

---

### 4. Network Communication Between Containers

#### How Services Talk to Each Other

```
Inside Docker's internal network:
┌──────────────────────────────────────────────────┐
│           Docker Bridge Network                  │
│                                                  │
│  customer-service:8081 ─────────┐               │
│                                  ▼               │
│  order-service:8083 ──────► config-server:8888  │
│  ▲                                               │
│  └──── Gets config from here                    │
│                                                  │
│  api-gateway:8080 ──► discovery-server:8761    │
│       Routes traffic                            │
└──────────────────────────────────────────────────┘

From OUTSIDE (your laptop):
localhost:8080 → only way to access system
(API Gateway is the single entry point)
```

**Key Point:** Services use **internal container names** (like `config-server`), not `localhost`

---

## 🔧 Real-World Example: Starting Everything

### Without Docker
```bash
# Terminal 1: Start Zookeeper
cd /opt/kafka && bin/zookeeper-server-start.sh config/zookeeper.properties

# Terminal 2: Start Kafka
cd /opt/kafka && bin/kafka-server-start.sh config/server.properties

# Terminal 3: Start PostgreSQL
psql -U postgres

# Terminal 4: Start Config Server
cd infrastructure/config-server && mvn spring-boot:run

# Terminal 5: Start Discovery Server
cd infrastructure/discovery-server && mvn spring-boot:run

# ... 5 more terminals for other services

# Time needed: 20-30 minutes
# Things that can go wrong: 50+
```

### With Docker
```bash
# Just one command!
docker-compose up -d

# Time needed: 2-3 minutes (first time), 30 seconds (after that)
# Things that can go wrong: Almost zero!
```

---

## ⚠️ Common Docker Issues in Your Project

### ❌ Issue 1: "Cannot connect to Discovery Server"

**Error Message:**
```
Customer Service logs show:
"Unable to connect to discovery server at http://discovery-server:8761/eureka/"
Connection refused
```

**Why This Happens:**
```yaml
# Wrong: Using localhost (only works inside that container)
- EUREKA_CLIENT_SERVICEURL_DEFAULTZONE=http://localhost:8761/eureka/

# Correct: Using service name (Docker DNS resolves it)
- EUREKA_CLIENT_SERVICEURL_DEFAULTZONE=http://discovery-server:8761/eureka/
```

**Fix:**
```bash
# Check environment variables in docker-compose.yml
# Make sure ALL services use service names, not localhost

# Verify with:
docker-compose exec customer-service bash
curl http://discovery-server:8761/eureka/  # Should work!
```

---

### ❌ Issue 2: "Kafka Connection Refused"

**Error Message:**
```
Order Service logs:
"Failed to connect to Kafka broker at kafka:9092"
```

**Why This Happens:**
```
Kafka has TWO listeners:
1. Internal (for containers): kafka:29092  ✅ Use this
2. External (for your laptop): localhost:9092  ✅ Use this from Docker Desktop

Your service uses wrong listener!
```

**Fix:**
```yaml
# Correct configuration in docker-compose.yml
order-service:
  environment:
    # For containers inside Docker network: port 29092
    - SPRING_KAFKA_BOOTSTRAP_SERVERS=kafka:29092
    
# For testing from your laptop: port 9092
# docker exec order-service kafka-console-consumer ... --bootstrap-servers kafka:9092
```

---

### ❌ Issue 3: "Port Already in Use"

**Error Message:**
```
Creating customer-service ... error
ERROR: for customer-service Cannot start service customer-service: 
Ports are not available: exposing port TCP 0.0.0.0:8081 -> 0.0.0.0:8081
```

**Why This Happens:**
```
Port 8081 is already used by:
- Another Docker container, OR
- Another application on your computer
```

**Fix:**
```bash
# Find what's using port 8081
netstat -ano | findstr :8081  # Windows
lsof -i :8081                  # macOS/Linux

# Option 1: Stop that application
docker stop container_id

# Option 2: Use different port
# In docker-compose.yml, change:
customer-service:
  ports:
    - "9081:8081"  # Host port 9081 → Container port 8081

# Option 3: Kill the process using that port
taskkill /PID process_id /F   # Windows
kill -9 process_id            # macOS/Linux
```

---

### ❌ Issue 4: "Config Server Not Ready"

**Error Message:**
```
Order Service logs:
"Waiting for config server to be ready..."
(keeps failing)
```

**Why This Happens:**
```
Timing issue:
1. docker-compose starts all services simultaneously
2. Config Server takes 15 seconds to be ready
3. Order Service tries to connect at 5 seconds
4. Connection fails because Config Server isn't ready yet
```

**Fix:**
```yaml
# Your docker-compose.yml uses depends_on:
depends_on:
  - config-server

# This only waits for container to START
# Not for service to be READY!

# Solution: Spring Boot has built-in retry logic
# Just wait and let it retry
# After 30-60 seconds, everything connects

# Or manually check:
docker logs config-server | grep "started"
docker logs customer-service | grep "Success"
```

---

### ❌ Issue 5: "Out of Disk Space"

**Error Message:**
```
docker-compose up
ERROR: ... no space left on device
```

**Why This Happens:**
```
Docker images and containers take space:
- Each microservice image: ~300MB
- Each container runtime: ~50MB
- Elasticsearch: ~500MB
- Your laptop has limited disk space
```

**Fix:**
```bash
# See what's using space
docker system df

# Remove unused images and containers
docker system prune -a

# Remove specific container/image
docker rm container_id
docker rmi image_id

# Check disk space
df -h                    # macOS/Linux
diskutil info /Volumes  # macOS
dir C:\                 # Windows
```

---

## 🔍 Debugging Docker Containers

### View Service Logs
```bash
# View logs from specific service
docker-compose logs customer-service

# View logs with timestamps
docker-compose logs -t customer-service

# Follow logs (like tail -f)
docker-compose logs -f order-service

# View last 100 lines
docker-compose logs --tail=100 payment-service
```

### Access Running Container
```bash
# Open bash shell inside container
docker-compose exec customer-service bash

# Inside the container, you can:
curl http://discovery-server:8761/eurator/apps  # Check Eureka
ps aux | grep java                              # Check Java process
env | grep SPRING_CONFIG                        # Check environment
cat /app/application.properties                 # Check config
```

### Check Container Health
```bash
# See if container is running
docker-compose ps

# Status will show:
# customer-service: Up (healthy) ✅
# customer-service: Up 30s (health: starting)
# customer-service: Exited (1) 2 min ago

# Check health status
docker-compose exec customer-service curl -f http://localhost:8081/actuator/health

# Should return:
# {"status":"UP"}
```

---

## 📊 Volume Mounts (File Sharing)

### Why You Need Volumes

```
Without volumes (data is lost):
┌─────────────────────────────────────┐
│     Container                       │
│  ├── Elasticsearch data             │
│  ├── Prometheus metrics             │
│  ├── Grafana dashboards             │
│  └── Application logs               │
│                                     │
│  When container stops:              │
│  ALL DATA DISAPPEARS ❌             │
└─────────────────────────────────────┘

With volumes (data persists):
┌────────────────────────┐  ┌──────────────────────────┐
│     Container          │  │   Your Computer (Disk)   │
│  ├── Elasticsearch     │─→│  ├── prometheus-data     │
│  ├── Prometheus        │─→│  ├── grafana-data        │
│  └── Grafana           │─→│  └── alertmanager-data   │
│                        │  │                          │
│  When container stops: │  │  DATA REMAINS ✅         │
│  Data is saved!        │  └──────────────────────────┘
└────────────────────────┘
```

### Your Project's Volumes
```yaml
# In docker-compose.yml
volumes:
  prometheus-data:
    driver: local        # Save to your computer
  grafana-data:
    driver: local
  alertmanager-data:
    driver: local
  loki-data:
    driver: local

# Services use these volumes:
services:
  prometheus:
    volumes:
      - prometheus-data:/prometheus  # Container → Volume → Your disk
      - ./monitoring/prometheus.yml:/etc/prometheus/prometheus.yml
      # Mount config file from your computer
```

### Where Data is Stored
```
Windows:
C:\Users\[YourUser]\AppData\Local\Docker\wsl\data\

macOS:
~/Library/Containers/com.docker.docker/Data/vms/0/

Linux:
/var/lib/docker/volumes/
```

---

## ✅ Good Docker Practices (Best Practices)

### 1. ✅ Keep Images Small
```dockerfile
# ❌ Bad
FROM ubuntu:22.04
RUN apt-get update && apt-get install -y \
    python3 python3-pip postgresql mysql \
    node npm java maven docker && \
    ...
# Final image: 2GB

# ✅ Good
FROM python:3.11-slim
RUN apt-get update && apt-get install -y \
    postgresql-client && \
    rm -rf /var/lib/apt/lists/*
# Final image: 200MB (10x smaller!)
```

### 2. ✅ Use Multi-Stage Builds
```dockerfile
# ❌ Without multi-stage
FROM maven:3.8-openjdk-17
RUN mvn clean package
# Image includes Maven (500MB+)

# ✅ With multi-stage (what your project does!)
FROM maven:3.8-openjdk-17 as builder
RUN mvn clean package

FROM openjdk:17-jre-slim
COPY --from=builder /build/target/*.jar app.jar
# Image only includes JRE (180MB)
```

### 3. ✅ Use Environment Variables
```dockerfile
# ✅ Configurable at runtime
ENV SERVER_PORT=8081
ENV JAVA_OPTS="-Xmx256m"
ENV LOG_LEVEL=INFO

# Later, you can override:
docker run -e LOG_LEVEL=DEBUG myapp
```

### 4. ✅ Add Health Checks
```dockerfile
# ✅ Docker automatically restarts unhealthy containers
HEALTHCHECK --interval=10s --timeout=5s --start-period=30s --retries=3 \
  CMD curl -f http://localhost:8081/actuator/health/readiness || exit 1
```

### 5. ✅ Use Specific Image Tags
```yaml
# ❌ Bad (latest changes unexpectedly)
image: elasticsearch:latest

# ✅ Good (pinned version)
image: elasticsearch:8.10.2
```

---

## 🎯 Summary of Your Docker Setup

| Aspect | Your Project |
|--------|--------------|
| **Container Orchestration** | Docker Compose (for local dev) |
| **Build Strategy** | Multi-stage builds (small images) |
| **Network** | Docker bridge network (internal DNS) |
| **Persistence** | Named volumes for databases & monitoring |
| **Health Checks** | Spring Boot Actuator + Curl |
| **Configuration** | Environment variables + Spring Cloud Config |
| **Services** | 16 total (5 microservices + infrastructure + monitoring) |
| **Development Flow** | One `docker-compose up -d` command |

---

## 🚀 Next Steps

**Ready to go deeper?**
- **DOCKER_VISUAL_GUIDE.md** - Visual diagrams and examples
- Learn about **Docker networking** in detail
- Understand **Docker volumes** and persistence
- Move to Kubernetes for production deployment

---

**Key Takeaway:** Docker makes your entire system reproducible and portable. Your project demonstrates real-world enterprise Docker usage! 🎉

