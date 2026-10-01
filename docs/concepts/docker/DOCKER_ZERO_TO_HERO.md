# Docker Zero to Hero 🦸
## Complete Mastery Guide - From Basics to Production

---

## Table of Contents
1. [Core Concepts](#core-concepts)
2. [Installation & Setup](#installation--setup)
3. [Your First Container](#your-first-container)
4. [Writing Dockerfiles](#writing-dockerfiles)
5. [Docker Compose Mastery](#docker-compose-mastery)
6. [Networking & Communication](#networking--communication)
7. [Storage & Volumes](#storage--volumes)
8. [Advanced Topics](#advanced-topics)
9. [Production Best Practices](#production-best-practices)
10. [Real-World Project Integration](#real-world-project-integration)
11. [Troubleshooting Guide](#troubleshooting-guide)

---

## Core Concepts

### Docker Three Pillars

#### 1. **Build**
```bash
docker build -t myapp:1.0 .
```
**What happens:**
- Reads Dockerfile
- Executes each instruction
- Creates intermediate containers
- Layers are cached
- Final image stored locally

**Key Points:**
- **Context**: Everything in current directory sent to Docker daemon
- **Caching**: Unchanged layers reused (fast rebuild!)
- **Build args**: Variables passed at build time

```dockerfile
# Dockerfile
ARG JAVA_VERSION=17
FROM openjdk:${JAVA_VERSION}-jre-slim
# Use ARG at build time
```

#### 2. **Ship**
```bash
docker push username/myapp:1.0
```
**What happens:**
- Image uploaded to registry
- Tags allow version management
- Multiple tags point to same image

**Registry options:**
- Docker Hub (public, free)
- GitHub Container Registry
- Amazon ECR
- Private registries (Nexus, Harbor)

```bash
# Tag and push to different registries
docker tag myapp:1.0 username/myapp:1.0
docker tag myapp:1.0 username/myapp:latest
docker push username/myapp:1.0
docker push username/myapp:latest
```

#### 3. **Run**
```bash
docker run -d -p 8080:8080 myapp:1.0
```
**What happens:**
- Container created from image
- Network configured
- Volumes mounted
- Environment variables set
- Process started

---

### Dockerfile Best Practices

#### Optimization Techniques

##### 1. Layer Caching Optimization
```dockerfile
# ❌ Bad - Rebuilds everything on code change
FROM openjdk:17-jre-slim
COPY . /app
RUN cd /app && mvn clean package

# ✅ Good - Only rebuilds Maven when dependencies change
FROM maven:3.8-openjdk-17 as builder
WORKDIR /build
COPY pom.xml .
RUN mvn dependency:go-offline  # Cache dependencies first
COPY . .
RUN mvn clean package

FROM openjdk:17-jre-slim
COPY --from=builder /build/target/*.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]
```

**Why this matters:**
- pom.xml changes rarely
- Dependencies cached
- Code changes rebuild only code layer
- Save 5-10 minutes per build!

##### 2. Multi-Stage Build Reduction
```dockerfile
# Stage 1: Build
FROM maven:3.8-openjdk-17 as builder
... build steps ...
# Output: builder-stage.jar (15MB)

# Stage 2: Runtime
FROM openjdk:17-jre-slim
COPY --from=builder /build/target/*.jar app.jar
# Only includes: JRE + JAR = 200MB
# Without multi-stage: Maven + JRE + code = 700MB!
```

##### 3. Minimal Base Images
```dockerfile
# ❌ Large base
FROM ubuntu:22.04
RUN apt-get update && apt-get install -y java ...
# Size: 500MB+

# ✅ Minimal base
FROM openjdk:17-jre-slim
# Size: 180MB
# Already has Java, smaller filesystem

# ✅ Even more minimal
FROM openjdk:17-jre-alpine
# Size: 100MB!
# Alpine Linux is tiny
```

##### 4. Clean Up Layers
```dockerfile
# ❌ Bad - Leaves files in layers
RUN apt-get update
RUN apt-get install -y curl
RUN apt-get install -y vim

# ✅ Good - Clean up after install
RUN apt-get update && \
    apt-get install -y curl vim && \
    rm -rf /var/lib/apt/lists/*
# Saves space by removing cache

# ✅ Better - Don't install unnecessary tools
RUN apt-get update && \
    apt-get install -y --no-install-recommends curl && \
    rm -rf /var/lib/apt/lists/*
```

##### 5. Explicit Dependencies
```dockerfile
# ❌ Vague versioning
FROM openjdk
FROM ubuntu:latest

# ✅ Specific versions
FROM openjdk:17-jre-slim
FROM ubuntu:22.04
# Prevents surprises when images update
```

---

## Installation & Setup

### Platform-Specific Setup

#### Windows
```bash
# Download Docker Desktop from docker.com
# Install .exe file
# Start Docker Desktop from Applications
# Verify installation
docker --version
docker run hello-world
```

**Windows-Specific Notes:**
- Docker Desktop requires WSL 2 (Windows Subsystem for Linux)
- Enable Hyper-V in Windows features
- Allocate sufficient memory to Docker (4-8GB recommended)
- Ports are accessible via `localhost`

#### macOS
```bash
# Using Homebrew
brew install docker docker-compose

# Or download Docker Desktop DMG
# Manual installation from docker.com

# Verify
docker --version
docker-compose --version
```

#### Linux
```bash
# Ubuntu/Debian
sudo apt-get update
sudo apt-get install -y docker.io docker-compose

# Add user to docker group (avoid sudo)
sudo usermod -aG docker $USER

# Verify
docker --version
```

### Configuration

#### Docker Daemon Configuration
File: `~/.docker/config.json` or `/etc/docker/daemon.json`

```json
{
  "registry-mirrors": [
    "https://mirror.aliyun.com"  // Faster image pulls in China
  ],
  "insecure-registries": [
    "myregistry.com:5000"        // Allow insecure registry
  ],
  "storage-driver": "overlay2",  // Efficient storage
  "live-restore": true,          // Keep containers alive on daemon restart
  "log-driver": "json-file",     // Logging driver
  "log-opts": {
    "max-size": "10m",           // Log rotation
    "max-file": "3"
  }
}
```

Restart Docker after changes:
```bash
sudo systemctl restart docker  # Linux
# macOS/Windows: Restart Docker Desktop UI
```

---

## Your First Container

### Complete Walkthrough

#### 1. Create Application
```java
// HelloApp.java
public class HelloApp {
    public static void main(String[] args) throws Exception {
        System.out.println("🐳 Hello from Docker!");
        System.out.println("Container running successfully!");
    }
}
```

#### 2. Write Dockerfile
```dockerfile
FROM openjdk:17-jre-slim

WORKDIR /app

COPY HelloApp.class .

ENTRYPOINT ["java", "HelloApp"]

HEALTHCHECK --interval=10s --timeout=5s \
  CMD [ "echo", "OK" ] || exit 1
```

#### 3. Build Image
```bash
# Compile Java first
javac HelloApp.java

# Build Docker image
docker build -t hello-app:1.0 .

# Verify image created
docker images
# Output:
# REPOSITORY  TAG    IMAGE ID       SIZE
# hello-app   1.0    abc123def456   180MB
```

#### 4. Run Container
```bash
# Run container
docker run hello-app:1.0

# Output:
# 🐳 Hello from Docker!
# Container running successfully!

# Verify container ran
docker ps -a
# Shows stopped container

# Run in background
docker run -d hello-app:1.0

# Run with name
docker run --name my-hello hello-app:1.0

# Run with port mapping (if app served HTTP)
docker run -p 8080:8080 hello-app:1.0
```

#### 5. Debugging
```bash
# View image layers
docker history hello-app:1.0

# Inspect image details
docker inspect hello-app:1.0

# Interactive shell in container
docker run -it hello-app:1.0 /bin/bash

# View logs
docker logs container_id
docker logs -f container_id  # Follow

# Execute command in running container
docker exec container_id java -version
```

---

## Writing Dockerfiles

### Complete Reference

#### Dockerfile Instructions

```dockerfile
# 1. FROM - Must be first instruction (except ARG/ENV)
FROM openjdk:17-jre-slim
# Inherits from this base image
# Every image must have a FROM

# 2. ARG - Build-time variables (not in container)
ARG BUILD_DATE
ARG VERSION=1.0.0
# Usage: docker build --build-arg VERSION=2.0.0

# 3. ENV - Environment variables (available in container)
ENV JAVA_OPTS="-Xmx256m"
ENV APP_HOME=/app
# Access in container: echo $JAVA_OPTS

# 4. WORKDIR - Working directory inside container
WORKDIR /app
# Like `cd /app` - subsequent instructions run here

# 5. COPY - Copy from host to container
COPY pom.xml .
COPY target/app.jar .
# First parameter: host path
# Second parameter: container path

# 6. ADD - Copy with extraction
ADD app.tar.gz .
# Automatically extracts tar files

# 7. RUN - Execute commands during build
RUN apt-get update && \
    apt-get install -y curl && \
    rm -rf /var/lib/apt/lists/*
# Each RUN creates a layer (minimize!)

# 8. EXPOSE - Document ports
EXPOSE 8080
# Just documentation, doesn't actually open ports

# 9. HEALTHCHECK - Check container health
HEALTHCHECK --interval=30s --timeout=10s --start-period=40s --retries=3 \
  CMD curl -f http://localhost:8080/health || exit 1
# Docker automatically restarts if unhealthy

# 10. ENTRYPOINT - Primary command (can't be overridden easily)
ENTRYPOINT ["java", "-jar", "app.jar"]
# Format: ["executable", "param1", "param2"]
# vs shell form: java -jar app.jar

# 11. CMD - Default command/parameters
CMD ["--spring.profiles.active=prod"]
# Provides default args to ENTRYPOINT
# Can be overridden: docker run myapp --custom-arg

# 12. LABEL - Metadata
LABEL maintainer="your-email@example.com"
LABEL version="1.0"
LABEL description="My microservice"

# 13. USER - Run as specific user
USER 1000  # Non-root for security
# Prevent container running as root

# 14. VOLUME - Mount point
VOLUME ["/data"]
# Declares this path should use volume

# 15. ONBUILD - Triggered in child images
ONBUILD RUN ./check-version.sh
# Runs only in images that use this as base
```

### Complete Real-World Dockerfile Example

```dockerfile
# Final Dockerfile for microservice

# ============ Stage 1: Build ============
FROM maven:3.8-openjdk-17 as builder

# Set build arguments
ARG BUILD_DATE
ARG VERSION=1.0.0
ARG GIT_COMMIT

# Add metadata
LABEL build.date=$BUILD_DATE
LABEL version=$VERSION
LABEL git.commit=$GIT_COMMIT

WORKDIR /build

# Copy dependency descriptors first (cacheable layer)
COPY pom.xml .
RUN mvn dependency:go-offline

# Copy source code
COPY . .

# Build application
RUN mvn clean package -DskipTests

# ============ Stage 2: Runtime ============
FROM openjdk:17-jre-slim

# Metadata
LABEL maintainer="devops@company.com"
LABEL description="Customer Service Microservice"

# Install runtime dependencies
RUN apt-get update && \
    apt-get install -y --no-install-recommends curl && \
    rm -rf /var/lib/apt/lists/*

# Create non-root user for security
RUN useradd -m -u 1000 appuser

# Set working directory
WORKDIR /app

# Copy built artifact from builder
COPY --from=builder /build/target/customer-service-*.jar app.jar

# Set ownership
RUN chown -R appuser:appuser /app

# Switch to non-root user
USER appuser

# Expose port
EXPOSE 8081

# Environment configuration
ENV JAVA_OPTS="-Xmx256m -Xms256m"
ENV SERVER_PORT=8081

# Health check
HEALTHCHECK --interval=10s --timeout=5s --start-period=30s --retries=3 \
  CMD curl -f http://localhost:8081/actuator/health/readiness || exit 1

# Startup command
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]

# Default parameters can be overridden
CMD ["--spring.profiles.active=docker"]
```

### Build Commands

```bash
# Simple build
docker build -t myapp .

# Build with tag
docker build -t myapp:1.0 .

# Build with build arguments
docker build -t myapp:1.0 \
  --build-arg VERSION=1.0.0 \
  --build-arg BUILD_DATE=$(date -u +'%Y-%m-%dT%H:%M:%SZ') \
  .

# Build with progress bar
docker build -t myapp --progress=plain .

# Build without cache
docker build --no-cache -t myapp .

# Build from Dockerfile with different name
docker build -f Dockerfile.prod -t myapp-prod .

# Multi-platform build
docker build --platform linux/amd64,linux/arm64 -t myapp .
```

---

## Docker Compose Mastery

### Complete docker-compose.yml Reference

```yaml
version: '3.8'  # Use latest version for features

services:
  # Microservice 1
  service1:
    # Build from Dockerfile
    build:
      context: ./services/service1
      dockerfile: Dockerfile
      args:
        VERSION: 1.0.0

    # Or use pre-built image
    # image: username/service1:1.0

    # Container name
    container_name: service1-app

    # Port mappings
    ports:
      - "8081:8081"      # Host:Container
      - "9090:9090"      # Multiple ports

    # Environment variables
    environment:
      - JAVA_OPTS=-Xmx512m
      - APP_ENV=docker
      - DEBUG=false

    # Or from env file
    env_file:
      - .env.docker

    # Volume mounts
    volumes:
      - ./data:/app/data
      - app-logs:/var/log
      - /var/run/docker.sock:/var/run/docker.sock  # Docker socket

    # Startup order
    depends_on:
      - service2
      - database
    
    # Service dependencies with conditions
    depends_on:
      service2:
        condition: service_healthy
      database:
        condition: service_healthy

    # Network configuration
    networks:
      - backend
      - frontend

    # Resource limits
    deploy:
      resources:
        limits:
          cpus: '1.0'
          memory: 512M
        reservations:
          cpus: '0.5'
          memory: 256M

    # Restart policy
    restart: unless-stopped  # unless-stopped, always, on-failure, no

    # Health check
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:8081/health"]
      interval: 30s
      timeout: 10s
      retries: 3
      start_period: 40s

    # Logging
    logging:
      driver: "json-file"
      options:
        max-size: "10m"
        max-file: "3"

  # Database service
  database:
    image: postgres:15-alpine
    container_name: postgres-db
    
    environment:
      POSTGRES_USER: admin
      POSTGRES_PASSWORD: password123
      POSTGRES_DB: service1_db

    volumes:
      - postgres-data:/var/lib/postgresql/data
      - ./init.sql:/docker-entrypoint-initdb.d/init.sql

    ports:
      - "5432:5432"

    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U admin"]
      interval: 10s
      timeout: 5s
      retries: 5

    networks:
      - backend

  # Message broker
  kafka:
    image: confluentinc/cp-kafka:7.3.2
    
    depends_on:
      - zookeeper

    environment:
      KAFKA_BROKER_ID: 1
      KAFKA_ZOOKEEPER_CONNECT: zookeeper:2181
      KAFKA_ADVERTISED_LISTENERS: PLAINTEXT://kafka:29092,PLAINTEXT_HOST://localhost:9092
      KAFKA_LISTENER_SECURITY_PROTOCOL_MAP: PLAINTEXT:PLAINTEXT,PLAINTEXT_HOST:PLAINTEXT
      KAFKA_INTER_BROKER_LISTENER_NAME: PLAINTEXT
      KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR: 1

    ports:
      - "9092:9092"

    networks:
      - backend

# Defined volumes
volumes:
  postgres-data:
    driver: local
  app-logs:
    driver: local

# Defined networks
networks:
  backend:
    driver: bridge
  frontend:
    driver: bridge
```

### Docker Compose Commands

```bash
# Start all services
docker-compose up -d

# Start with specific services
docker-compose up -d service1 database

# View status
docker-compose ps

# View logs
docker-compose logs
docker-compose logs -f service1      # Follow service1 logs
docker-compose logs --tail=50        # Last 50 lines

# Execute command in service
docker-compose exec service1 bash
docker-compose exec database psql -U admin

# Restart services
docker-compose restart
docker-compose restart service1

# Stop services
docker-compose stop                  # Graceful stop
docker-compose kill                  # Force stop

# Remove containers
docker-compose down                  # Stop and remove
docker-compose down -v               # Remove volumes too
docker-compose down --rmi all        # Remove images too

# Rebuild images
docker-compose build
docker-compose build --no-cache

# View configuration
docker-compose config               # Show merged config
docker-compose config --services    # List services
```

---

## Networking & Communication

### Network Types

#### 1. Bridge Network (Default)
```bash
# Create bridge network
docker network create mynet

# Connect containers
docker run --network mynet --name app1 myapp:1.0
docker run --network mynet --name app2 myapp:1.0

# Containers can communicate via names
# Inside app1: curl http://app2:8080/api

# List networks
docker network ls

# Inspect network
docker network inspect mynet
```

#### 2. Host Network
```bash
# Container uses host networking
docker run --network host myapp:1.0
# Container ports are directly exposed on host
# Performance: Highest (no translation overhead)
# Isolation: Lowest (not recommended)
```

#### 3. None Network
```bash
# No network connectivity
docker run --network none myapp:1.0
# Useful for isolated batch jobs
```

### Service Discovery

In docker-compose:
```yaml
services:
  app:
    environment:
      # Services are accessible by name
      DATABASE_URL: postgresql://database:5432/mydb
      KAFKA_BOOTSTRAP: kafka:9092
      # NO hardcoded IPs needed!
```

---

## Storage & Volumes

### Volume Types

#### 1. Named Volumes
```bash
# Create volume
docker volume create mydata

# Use in compose
volumes:
  mydata:
    driver: local

# Use in container
docker run -v mydata:/data myapp

# List volumes
docker volume ls

# Inspect volume
docker volume inspect mydata

# Remove volume
docker volume rm mydata
```

#### 2. Bind Mounts
```bash
# Mount host directory
docker run -v /host/path:/container/path myapp

# In docker-compose
volumes:
  - ./local/folder:/app/data

# Read-only mount
docker run -v /host/path:/container/path:ro myapp
```

#### 3. Anonymous Volumes
```bash
# Volume created but not named
docker run -v /app/data myapp

# Docker auto-generates name
# Hard to reference later
# Generally avoid
```

### Data Persistence Strategy

```
Development:
├── Use bind mounts for code
│   volumes:
│     - ./src:/app/src  # Edit code, see changes instantly
│
└── Use named volumes for databases
    volumes:
      - postgres-data:/var/lib/postgresql/data

Production:
├── Use named volumes
│   └── Managed by container orchestration
│
└── Use external storage
    ├── Cloud provider storage (S3, GCS)
    ├── NFS mounts
    └── Database services
```

---

## Advanced Topics

### 1. Multi-Container Debugging

```bash
# Get container IP
docker inspect container_id | grep IPAddress

# Network commands inside container
docker exec container_id ip addr
docker exec container_id netstat -an
docker exec container_id route

# DNS resolution
docker exec container_id nslookup service_name
docker exec container_id cat /etc/hosts

# Test connectivity
docker exec container_id curl http://other-service:8080/health
```

### 2. Resource Limiting

```yaml
# Limit resources
deploy:
  resources:
    limits:
      cpus: '2.0'           # Max 2 CPU cores
      memory: 1024M         # Max 1GB memory
    reservations:
      cpus: '1.0'           # Request 1 CPU
      memory: 512M          # Request 512MB

# Monitor resource usage
docker stats
docker stats container_id
```

### 3. Image Optimization

```bash
# Check image size
docker images myapp

# Analyze layers
docker history myapp:1.0

# Find unused layers
docker image prune

# Save/load images
docker save myapp:1.0 > myapp.tar
docker load < myapp.tar

# Tag images for registry
docker tag myapp:1.0 registry.com/username/myapp:1.0
docker tag myapp:1.0 registry.com/username/myapp:latest
```

### 4. Secrets and Configuration

```bash
# Using environment files
docker run --env-file .env myapp

# Using secrets (Swarm)
docker secret create db_password ./password.txt

# Using config (Swarm)
docker config create app.conf ./app.conf

# Best practice: Don't hardcode secrets
# Use:
# 1. Environment variables
# 2. Config files (mounted as volumes)
# 3. Secret management (HashiCorp Vault, AWS Secrets Manager)
# 4. Docker Secrets / Kubernetes Secrets
```

---

## Production Best Practices

### 1. Security

```dockerfile
# ✅ Run as non-root user
USER 1000

# ✅ Use specific base image versions
FROM openjdk:17.0.2-jre-slim

# ✅ Scan for vulnerabilities
docker scan myapp:1.0

# ✅ Sign images
docker trust sign username/myapp:1.0

# ✅ Use read-only root filesystem
docker run --read-only --tmpfs /tmp myapp

# ✅ Drop unnecessary capabilities
docker run --cap-drop ALL --cap-add NET_BIND_SERVICE myapp

# ✅ Don't expose sensitive files
# In .dockerignore:
# .git
# .env
# secrets/
# node_modules/
```

### 2. Monitoring & Logging

```bash
# Centralized logging
docker run --log-driver splunk myapp

# Metrics collection
docker stats --no-stream  # One-time stats
docker stats             # Continuous monitoring

# Docker events
docker events --filter container=myapp

# Check container health
docker ps  # Check STATUS column for (healthy)
```

### 3. Automation

```bash
# Docker in CI/CD
git push origin main
  ↓
CI Pipeline:
  1. docker build -t app:${GIT_COMMIT} .
  2. docker push registry.com/app:${GIT_COMMIT}
  3. kubectl set image deployment/app app=registry.com/app:${GIT_COMMIT}

# GitHub Actions example
name: Build and Push
on: [push]
jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v2
      - uses: docker/build-push-action@v3
        with:
          push: true
          tags: registry.com/app:${{ github.sha }}
```

### 4. Container Orchestration

For production, use:
- **Kubernetes (K8s)** - Industry standard, powerful
- **Docker Swarm** - Simpler, built-in to Docker
- **AWS ECS** - Managed AWS service
- **Fargate** - Serverless containers

Your project is already set up for Kubernetes! (See k8s/ folder)

---

## Real-World Project Integration

### Your micro-eCommerce Project

#### Working with docker-compose.yml

```bash
# Start entire system
docker-compose up -d

# Verify all services running
docker-compose ps

# Check specific service logs
docker-compose logs customer-service
docker-compose logs -f order-service

# Access a service
docker-compose exec customer-service bash
curl http://inventory-service:8082/api/inventory

# Rebuild services
docker-compose build
docker-compose build customer-service  # Just one service
docker-compose up -d --build            # Build and start

# Stop and clean up
docker-compose down
docker-compose down -v                  # Remove volumes too
```

#### Debugging Your Services

```bash
# Check if Eureka discovered services
docker-compose exec api-gateway curl http://discovery-server:8761/eureka/apps

# Test inter-service communication
docker-compose exec order-service \
  curl http://inventory-service:8082/actuator/health

# Check Kafka connectivity
docker-compose exec payment-service \
  curl http://kafka:29092/

# View all environment variables
docker-compose exec customer-service env | grep SPRING

# Check database connectivity
docker-compose exec customer-service \
  curl http://localhost:8081/actuator/db
```

#### Adding New Services

```yaml
# Add to docker-compose.yml
  new-service:
    build:
      context: ./services/new-service
    container_name: new-service
    ports:
      - "8086:8086"
    depends_on:
      - config-server
      - discovery-server
      - kafka
    environment:
      - SPRING_CONFIG_IMPORT=optional:configserver:http://config-server:8888/
      - EUREKA_CLIENT_SERVICEURL_DEFAULTZONE=http://discovery-server:8761/eureka/
      - SPRING_KAFKA_BOOTSTRAP_SERVERS=kafka:29092
    networks:
      - default  # Auto-joined
```

---

## Troubleshooting Guide

### Common Issues & Solutions

#### Issue: Container Won't Start
```bash
# 1. Check logs
docker logs container_id

# 2. Check error message
docker logs container_id 2>&1 | tail -20

# 3. Run interactively
docker run -it image_id /bin/bash

# 4. Check dependencies
docker-compose up  # Not -d to see startup
```

#### Issue: Port Already in Use
```bash
# 1. Find what's using port
lsof -i :8080        # macOS/Linux
netstat -ano | findstr :8080  # Windows

# 2. Options:
# a) Kill the process
kill -9 process_id

# b) Use different port
docker run -p 8081:8080 myapp

# c) Stop Docker container
docker stop container_id
```

#### Issue: No Network Connectivity
```bash
# 1. Check service name
docker-compose exec app ping service_name

# 2. Check if service running
docker-compose ps

# 3. Check DNS resolution
docker-compose exec app nslookup service_name

# 4. Check network
docker network ls
docker network inspect network_name
```

#### Issue: Slow Performance
```bash
# 1. Check resource usage
docker stats
docker stats --no-stream

# 2. Check disk space
docker system df

# 3. Increase memory/CPU
# In docker-compose.yml:
deploy:
  resources:
    limits:
      memory: 2G
      cpus: '2'

# 4. Check I/O
docker stats  # Look for BLOCK I/O column
```

#### Issue: Disk Space Problems
```bash
# 1. Check usage
docker system df
docker system df -v  # Detailed

# 2. Clean unused resources
docker system prune          # Remove dangling objects
docker system prune -a       # Remove all unused
docker volume prune          # Remove unused volumes
docker image prune -a        # Remove unused images

# 3. Remove specific items
docker rmi image_id          # Remove image
docker rm container_id       # Remove container
docker volume rm volume_id   # Remove volume
```

### Debug Checklist

```
When something breaks, check in order:
□ Is Docker daemon running?  (docker ps)
□ Is container running?       (docker ps -a)
□ Check container logs        (docker logs)
□ Check container health      (docker inspect → Health)
□ Test connectivity           (docker exec curl)
□ Check resource limits       (docker stats)
□ Check disk space            (docker system df)
□ Check port mapping          (docker port)
□ Check networks              (docker network inspect)
□ Check volumes               (docker volume inspect)
□ Rebuild image               (docker build --no-cache)
□ Restart everything          (docker-compose down && up)
```

---

## 📊 Docker Command Cheat Sheet

### Images
```bash
docker build -t name:tag .                     # Build
docker images                                   # List
docker pull name:tag                           # Download
docker push name:tag                           # Upload
docker rmi image_id                            # Remove
docker tag source_id repo/name:tag             # Tag
docker inspect image_id                        # Details
docker history image_id                        # Layers
```

### Containers
```bash
docker run [OPTIONS] image [COMMAND]           # Create & run
docker ps                                      # Running
docker ps -a                                   # All
docker logs container_id                       # Logs
docker exec container_id command               # Run command
docker stop container_id                       # Graceful stop
docker kill container_id                       # Force stop
docker rm container_id                         # Delete
docker restart container_id                    # Restart
docker pause container_id                      # Pause
docker unpause container_id                    # Unpause
docker inspect container_id                    # Details
docker stats container_id                      # Resources
```

### Compose
```bash
docker-compose up -d                           # Start
docker-compose down                            # Stop
docker-compose ps                              # Status
docker-compose logs service_name               # Logs
docker-compose exec service bash               # Access
docker-compose build                           # Build
docker-compose restart                         # Restart
docker-compose down -v                         # Remove volumes
```

---

## 🎯 Key Principles to Remember

1. **Immutability**: Images don't change, versions don't change
2. **Layering**: Each instruction creates a layer (cache for speed)
3. **Isolation**: Containers don't affect each other
4. **Portability**: Same image everywhere
5. **Stateless**: Containers should be ephemeral (data in volumes)
6. **Reproducibility**: Same image = same behavior always
7. **Security**: Minimize attack surface

---

## 🚀 Next Steps in Your Journey

### Immediate Next Steps
1. ✅ Understand Dockerfile anatomy (this guide)
2. ✅ Master docker-compose (this guide)
3. ✅ Debug your microservices (this guide)

### Short-Term
- Learn Kubernetes basics
- Implement health checks
- Set up logging aggregation

### Medium-Term
- Container orchestration on Kubernetes
- Service mesh (Istio/Linkerd)
- Advanced networking

### Long-Term
- Serverless containers
- GitOps deployment
- Advanced observability

---

## Final Thoughts

Docker is a journey, not a destination. You've learned:
✅ Core concepts and architecture
✅ Building and running containers
✅ Multi-container systems (docker-compose)
✅ Networking and communication
✅ Storage and persistence
✅ Production best practices
✅ Troubleshooting

**You are now ready to:**
- Deploy your micro-eCommerce project
- Manage multiple containers
- Scale services
- Monitor and debug
- Optimize performance

**Remember:** Every expert Docker user started where you are now. Practice these concepts on your project, make mistakes (safely), and learn from them!

---

## 📚 Additional Resources

- Docker Official Docs: https://docs.docker.com
- Docker Hub: https://hub.docker.com
- Play with Docker: https://labs.play-with-docker.com
- Docker Compose Docs: https://docs.docker.com/compose/
- Your Project (k8s/ folder): Next step after Docker!

---

**Congratulations! You're now a Docker Hero! 🦸** 

Use this knowledge wisely to build robust, scalable, and portable systems. Happy containerizing! 🐳

