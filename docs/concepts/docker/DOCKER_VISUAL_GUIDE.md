# Docker Visual Guide 📊
## Complete Visual Explanations & Practical Examples

---

## 1. Docker Architecture Visual

```
┌─────────────────────────────────────────────────────────────────┐
│                    Your Computer (Host)                         │
│                                                                 │
│  ┌────────────────────────────────────────────────────────┐   │
│  │              Docker Desktop App                        │   │
│  │  ┌─────────────────────────────────────────────────┐  │   │
│  │  │        Docker Engine (Daemon)                   │  │   │
│  │  │  Manages images, containers, networks, volumes  │  │   │
│  │  │                                                 │  │   │
│  │  │  ┌──────────────────────────────────────────┐  │  │   │
│  │  │  │   Container 1 (Customer Service)        │  │  │   │
│  │  │  │  ├── Ubuntu Base Image                  │  │  │   │
│  │  │  │  ├── Java Runtime                       │  │  │   │
│  │  │  │  ├── Customer App JAR                   │  │  │   │
│  │  │  │  └── Port: 8081                         │  │  │   │
│  │  │  └──────────────────────────────────────────┘  │  │   │
│  │  │                                                 │  │   │
│  │  │  ┌──────────────────────────────────────────┐  │  │   │
│  │  │  │   Container 2 (Order Service)           │  │  │   │
│  │  │  │  ├── Ubuntu Base Image                  │  │  │   │
│  │  │  │  ├── Java Runtime                       │  │  │   │
│  │  │  │  ├── Order App JAR                      │  │  │   │
│  │  │  │  └── Port: 8083                         │  │  │   │
│  │  │  └──────────────────────────────────────────┘  │  │   │
│  │  │                                                 │  │   │
│  │  │  ┌──────────────────────────────────────────┐  │  │   │
│  │  │  │   Container 3 (Kafka)                    │  │  │   │
│  │  │  │  ├── Ubuntu Base Image                  │  │  │   │
│  │  │  │  ├── Java Runtime                       │  │  │   │
│  │  │  │  ├── Kafka Broker                       │  │  │   │
│  │  │  │  └── Port: 9092                         │  │  │   │
│  │  │  └──────────────────────────────────────────┘  │  │   │
│  │  │          ↑  ↑  ↑                                │  │   │
│  │  │    All containers share host OS kernel         │  │   │
│  │  │    (Much lighter than virtual machines!)       │  │   │
│  │  └─────────────────────────────────────────────────┘  │   │
│  │              ↓                                          │   │
│  │  Storage: /var/lib/docker/                            │   │
│  │  ├── images/     (Blueprint templates)               │   │
│  │  ├── containers/ (Running instances)                 │   │
│  │  └── volumes/    (Persistent data)                   │   │
│  └────────────────────────────────────────────────────────┘   │
│                                                                 │
│  Applications on Host (not containerized):                     │
│  ├── Chrome browser                                            │
│  ├── VS Code editor                                            │
│  └── Other programs (coexist with Docker!)                    │
└─────────────────────────────────────────────────────────────────┘
```

---

## 2. Image vs Container Visual

### Building an Image (Like a Blueprint)

```
Dockerfile (Instructions)
│
├── FROM openjdk:17-jre-slim
│   └── Download base image (~180MB)
│
├── WORKDIR /app
│   └── Create folder inside
│
├── COPY target/app.jar .
│   └── Add application
│
├── EXPOSE 8081
│   └── Document port
│
└── ENTRYPOINT ["java", "-jar", "app.jar"]
    └── Define startup command

                    ▼
            
        ┌─────────────────────┐
        │   Docker Image      │
        │   (Blueprint)       │
        │                     │
        │ Layers:             │
        │ 1. Ubuntu           │
        │ 2. Java             │
        │ 3. Application      │
        │ 4. Config           │
        │                     │
        │ Size: ~300MB        │
        │ (Read-only)         │
        └─────────────────────┘
```

### Running Containers (Like Baking Cookies)

```
   Image (Recipe)
        ▼
┌────────────────────────────────────────────┐
│  docker run -p 8081:8081 myapp:1.0        │
│  docker run -p 8081:8081 myapp:1.0        │
│  docker run -p 8080:8081 myapp:1.0        │
└────────────────────────────────────────────┘
        ▼  ▼  ▼

┌──────────────┐  ┌──────────────┐  ┌──────────────┐
│ Container 1  │  │ Container 2  │  │ Container 3  │
│              │  │              │  │              │
│ Image Copy   │  │ Image Copy   │  │ Image Copy   │
│ (Writable)   │  │ (Writable)   │  │ (Writable)   │
│              │  │              │  │              │
│ State:       │  │ State:       │  │ State:       │
│ Running ✅   │  │ Running ✅   │  │ Running ✅   │
│ Port: 8081   │  │ Port: 8081   │  │ Port: 8080   │
└──────────────┘  └──────────────┘  └──────────────┘

Each container is independent!
If Container 1 crashes, others keep running.
```

---

## 3. Microservices Communication Flow

```
CLIENT REQUEST
     │
     ▼
┌─────────────────────────────────────┐
│  http://localhost:8080/api/orders  │
│  (Your computer - Host)             │
└──────────────┬──────────────────────┘
               │
      Port Mapping (8080→8080)
               │
               ▼
┌───────────────────────────────────────────────────┐
│         Docker Bridge Network                     │
│         (Internal DNS: api-gateway)               │
│                                                   │
│     ┌─────────────────────────────────────────┐  │
│     │     API Gateway Container               │  │
│     │     localhost:8080 (inside container)  │  │
│     │                                         │  │
│     │  Routes request to:                    │  │
│     │  - /api/orders → order-service         │  │
│     │  - /api/customers → customer-service   │  │
│     │  - /api/payments → payment-service     │  │
│     └────────────┬──────────────────────────┘  │
│                  │                              │
│      ┌───────────┼───────────┬──────────────┐  │
│      ▼           ▼           ▼              ▼  │
│  ┌─────────┐┌──────────┐┌─────────┐┌──────────┐│
│  │Customer ││ Order    ││ Payment ││Inventory ││
│  │Service  ││ Service  ││ Service ││Service   ││
│  │:8081    ││ :8083    ││ :8084   ││:8082     ││
│  └─────────┘└──────────┘└─────────┘└──────────┘│
│      │           │           │          │      │
│      └───────────┼───────────┼──────────┘      │
│                  ▼                              │
│         Kafka (Message Broker)                  │
│         Events: OrderCreated,                  │
│                 PaymentProcessed,              │
│                 InventoryReserved              │
│                                                   │
└───────────────────────────────────────────────────┘
               │
      Port Mapping (back to host)
               │
               ▼
        Response to Client
```

---

## 4. Data Flow Through Your System

### Scenario: Customer Places an Order

```
┌─────────────────────────────────────────────────────────────────┐
│                      STEP 1: Order Creation                     │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  Client (Browser)                                               │
│  │                                                              │
│  ▼ POST /api/orders                                            │
│  ┌────────────────────────────────────────────┐                │
│  │   API Gateway (8080)                       │                │
│  │   - Validates JWT token                    │                │
│  │   - Rate limit check                       │                │
│  │   - Forwards to Order Service              │                │
│  └────────────────┬───────────────────────────┘                │
│                   │                                             │
│                   ▼ http://order-service:8083                 │
│  ┌────────────────────────────────────────────┐                │
│  │   Order Service                            │                │
│  │   - Creates order                          │                │
│  │   - Saves to database                      │                │
│  │   - Publishes OrderCreatedEvent            │                │
│  └────────────────┬───────────────────────────┘                │
│                   │                                             │
│                   ▼ (Kafka Topic: order.created)              │
│  ┌────────────────────────────────────────────┐                │
│  │   Kafka Event Broker                       │                │
│  │   Message:                                 │                │
│  │   {                                        │                │
│  │     "orderId": "12345",                   │                │
│  │     "customerId": "user-1",                │                │
│  │     "amount": 99.99,                       │                │
│  │     "timestamp": "2026-10-01T16:26:00Z"   │                │
│  │   }                                        │                │
│  └────────────────────────────────────────────┘                │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────┐
│            STEP 2: Payment Service Processes Event              │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  ┌────────────────────────────────────────────┐                │
│  │   Kafka Consumer                           │                │
│  │   (Listening to order.created)             │                │
│  └────────────────┬───────────────────────────┘                │
│                   │                                             │
│                   ▼ Receives event                             │
│  ┌────────────────────────────────────────────┐                │
│  │   Payment Service                          │                │
│  │   - Reads OrderCreatedEvent                │                │
│  │   - Validates payment                      │                │
│  │   - Charges customer                       │                │
│  │   - Publishes PaymentProcessedEvent        │                │
│  └────────────────┬───────────────────────────┘                │
│                   │                                             │
│                   ▼ (Kafka Topic: payment.processed)          │
│  ┌────────────────────────────────────────────┐                │
│  │   Kafka                                    │                │
│  │   Message: Payment succeeded               │                │
│  └────────────────────────────────────────────┘                │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────┐
│         STEP 3: Inventory Service Processes Event               │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  ┌────────────────────────────────────────────┐                │
│  │   Kafka Consumer                           │                │
│  │   (Listening to payment.processed)         │                │
│  └────────────────┬───────────────────────────┘                │
│                   │                                             │
│                   ▼ Receives event                             │
│  ┌────────────────────────────────────────────┐                │
│  │   Inventory Service                        │                │
│  │   - Reads PaymentProcessedEvent            │                │
│  │   - Reserves stock                         │                │
│  │   - Updates stock levels                   │                │
│  │   - Publishes InventoryReservedEvent       │                │
│  └────────────────┬───────────────────────────┘                │
│                   │                                             │
│                   ▼ (Kafka Topic: inventory.reserved)         │
│  ┌────────────────────────────────────────────┐                │
│  │   Kafka                                    │                │
│  │   Message: Stock reserved                  │                │
│  └────────────────────────────────────────────┘                │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘

Final Result:
✅ Order Created
✅ Payment Processed
✅ Stock Reserved
✅ Entire transaction completed asynchronously!
```

---

## 5. Docker Volumes & Persistence

```
Without Volumes (Data Lost):
┌─────────────────────────┐
│   Docker Container      │
│  ┌───────────────────┐  │
│  │  Elasticsearch    │  │
│  │  - All documents  │  │
│  │  - Indexes        │  │
│  │                   │  │
│  │ When you run:     │  │
│  │ docker-compose    │  │
│  │ down              │  │
│  │                   │  │
│  │ POOF! 💨          │  │
│  │ All data gone ❌  │  │
│  └───────────────────┘  │
└─────────────────────────┘


With Volumes (Data Persists):
┌──────────────────────┐       ┌──────────────────────────┐
│  Docker Container    │       │   Your Computer Disk     │
│  ┌────────────────┐  │       │  ┌──────────────────────┐│
│  │ Elasticsearch  │  │◄─────►│  │ elasticsearch-data   ││
│  │ (writes data)  │  │       │  │ /var/lib/docker/...  ││
│  │                │  │       │  │                      ││
│  │ Connected to:  │  │       │  │ Data survives ✅      ││
│  │ Volume Mount   │  │       │  └──────────────────────┘│
│  └────────────────┘  │       │                          │
│                      │       │  When docker down:       │
│ When you run:        │       │  Data remains!           │
│ docker-compose down  │       │                          │
│ Container stops      │       │  docker-compose up       │
│                      │       │  Data restored!          │
└──────────────────────┘       └──────────────────────────┘
```

---

## 6. Multi-Stage Build Process

```
Source Code
│
├── pom.xml (Maven config)
├── src/ (Your code)
│   ├── java/
│   └── resources/
│
▼

┌─────────────────────────────────────────┐
│  STAGE 1: BUILD (Maven Builder)         │
│                                         │
│  Docker Image: maven:3.8-openjdk-17    │
│  Size: ~500MB                           │
│                                         │
│  ├── Copies source code                 │
│  ├── Downloads dependencies (pom.xml)  │
│  ├── Compiles Java code                │
│  ├── Runs unit tests                    │
│  ├── Packages into JAR                  │
│  └── Creates: customer-service-1.0.jar │
│      (Size: ~15MB)                      │
└────────────┬────────────────────────────┘
             │
             ▼ Extract JAR
      ┌──────────────────┐
      │ customer-service │
      │    -1.0.jar      │
      │   (15MB) ✅       │
      └──────────────────┘
             │
             ▼

┌─────────────────────────────────────────┐
│  STAGE 2: RUNTIME (JRE Only)            │
│                                         │
│  Docker Image: openjdk:17-jre-slim     │
│  Size: ~180MB                           │
│                                         │
│  ├── Copies ONLY the JAR                │
│  ├── NO Maven (not needed to run!)      │
│  ├── NO source code (not needed!)       │
│  └── NO build tools (not needed!)       │
│                                         │
│  Final Image Size: ~200MB ✅            │
│  (NOT 500MB! Saved 60% space!)         │
└─────────────────────────────────────────┘
             │
             ▼
      ┌──────────────────┐
      │  Docker Image    │
      │  customer-service│
      │  (200MB)         │
      │  Ready to run! ✅ │
      └──────────────────┘
```

**Why This Matters:**
- Download faster ⚡
- Store more images 💾
- Deploy faster to cloud ☁️
- Save bandwidth 📡

---

## 7. Service Discovery & Registration

```
┌──────────────────────────────────────────────────┐
│              Eureka Server (8761)                │
│         (Service Registry)                       │
│                                                  │
│  ┌──────────────────────────────────────────┐   │
│  │  Registered Services:                    │   │
│  │                                          │   │
│  │  ✅ customer-service                    │   │
│  │     - URL: http://customer-service:8081 │   │
│  │     - Health: UP                         │   │
│  │     - Instances: 1                       │   │
│  │                                          │   │
│  │  ✅ order-service                       │   │
│  │     - URL: http://order-service:8083    │   │
│  │     - Health: UP                         │   │
│  │     - Instances: 1                       │   │
│  │                                          │   │
│  │  ✅ payment-service                     │   │
│  │     - URL: http://payment-service:8084  │   │
│  │     - Health: UP                         │   │
│  │     - Instances: 1                       │   │
│  │                                          │   │
│  │  ✅ inventory-service                   │   │
│  │     - URL: http://inventory-service:8082│   │
│  │     - Health: UP                         │   │
│  │     - Instances: 1                       │   │
│  └──────────────────────────────────────────┘   │
└──────────────────────────────────────────────────┘

When Order Service needs to call Inventory Service:
┌────────────────────────────────────────────┐
│  Order Service                             │
│  (inside container)                        │
│                                            │
│  Need to call inventory-service:           │
│                                            │
│  1. Ask Eureka: "Where is inventory        │
│     -service?"                             │
│                                            │
│  2. Eureka responds: "http://              │
│     inventory-service:8082"                │
│                                            │
│  3. Order Service connects to that URL     │
│                                            │
│  4. ✅ Success! No hardcoding needed!     │
└────────────────────────────────────────────┘

Benefits:
✅ No hardcoded IP addresses
✅ If service moves, Eureka knows
✅ Automatic load balancing
✅ Health checks automatic
```

---

## 8. Port Mapping Explained

```
Network Layers:
┌────────────────────────────────────────────────┐
│  Layer 1: Host Machine (Your Computer)        │
│  localhost:8080                                │
│  localhost:8081                                │
│  localhost:8082                                │
│  localhost:8083                                │
│  localhost:8084                                │
└────────────────┬───────────────────────────────┘
                 │ Port Mapping (docker-compose.yml)
                 │
┌────────────────▼───────────────────────────────┐
│  Layer 2: Docker Bridge Network (Internal)    │
│                                                │
│  customer-service:8081                         │
│  order-service:8083                            │
│  payment-service:8084                          │
│  inventory-service:8082                        │
│  api-gateway:8080                              │
└────────────────────────────────────────────────┘

Port Mapping Rules:
"HOST_PORT:CONTAINER_PORT"

Example from docker-compose.yml:
┌──────────────────────────────────────────────┐
│  customer-service:                           │
│    ports:                                    │
│      - "8081:8081"                           │
│        ↑     ↑                               │
│      Host  Container                         │
│      Port  Port                              │
│                                              │
│  So:                                         │
│  ✅ localhost:8081 → container port 8081   │
│  ✅ You can access app from browser         │
│                                              │
│  Without port mapping:                       │
│  ❌ localhost:8081 → doesn't work           │
│  ❌ Container isolated from outside         │
└──────────────────────────────────────────────┘

Internal Communication:
┌──────────────────────────────────────────────┐
│  Inside Docker Network (container-to-       │
│  container):                                 │
│                                              │
│  Order Service needs Kafka:                  │
│  ✅ Connect to: kafka:29092                 │
│     (NOT kafka:9092 - that's for host)      │
│                                              │
│  Config Server config:                       │
│  ✅ EUREKA_CLIENT_SERVICEURL_DEFAULTZONE=   │
│     http://discovery-server:8761/eureka/    │
│     (Use container name, not IP!)            │
│                                              │
│  Why different ports for Kafka?              │
│  - 9092: External (from your laptop)        │
│  - 29092: Internal (between containers)     │
│    Different networks need different ports!  │
└──────────────────────────────────────────────┘
```

---

## 9. Container Lifecycle States

```
┌─────────────┐
│  No Image   │
└──────┬──────┘
       │ docker build -t myapp .
       ▼
┌──────────────────────────────────┐
│  Image Created ✅                │
│  (Stored locally)                │
│  - myapp:1.0                     │
│  - Size: 300MB                   │
│  - Status: Ready to run          │
└──────┬───────────────────────────┘
       │ docker run myapp:1.0
       ▼
┌──────────────────────────────────┐
│  Container Running ✅            │
│  - ID: abc123def456              │
│  - Name: (can set with --name)   │
│  - Status: Up 2 minutes          │
│  - Ports: 8081/tcp               │
│  - CPU: Using X%                 │
│  - Memory: Using Y MB            │
└──────┬───────────────────────────┘
       │
       ├─ (If error or stop signal)
       │
       ▼
┌──────────────────────────────────┐
│  Container Stopped 🛑            │
│  - Data still in container       │
│  - Can restart with:             │
│    docker restart abc123         │
└──────┬───────────────────────────┘
       │
       ├─ docker rm abc123
       │
       ▼
┌──────────────────────────────────┐
│  Container Deleted ✋            │
│  - Container removed             │
│  - Data lost (unless volume)     │
│  - Image still exists             │
└──────────────────────────────────┘


docker-compose commands:
┌──────────────────────────────────────────────┐
│  docker-compose up -d                        │
│  └─ Create and START all containers         │
│                                              │
│  docker-compose ps                           │
│  └─ Show running containers                 │
│                                              │
│  docker-compose logs customer-service       │
│  └─ View container output                   │
│                                              │
│  docker-compose exec customer-service bash  │
│  └─ Run command inside container            │
│                                              │
│  docker-compose down                         │
│  └─ STOP and REMOVE all containers         │
│     (But volumes persist!)                  │
│                                              │
│  docker-compose down -v                      │
│  └─ STOP, REMOVE containers AND volumes    │
│     (Everything cleaned up!)                │
└──────────────────────────────────────────────┘
```

---

## 10. Practical: Building Your First Container

### Step-by-Step Example

```bash
# Step 1: Create a simple Java app
# File: HelloApp.java
public class HelloApp {
    public static void main(String[] args) {
        System.out.println("Hello from Docker!");
    }
}

# Step 2: Compile
javac HelloApp.java

# Step 3: Create Dockerfile
# File: Dockerfile
┌─────────────────────────────────────┐
│ FROM openjdk:17-jre-slim            │
│ WORKDIR /app                        │
│ COPY HelloApp.class .               │
│ EXPOSE 8080                         │
│ CMD ["java", "HelloApp"]            │
└─────────────────────────────────────┘

# Step 4: Build image
$ docker build -t hello-app:1.0 .

# Output:
# Step 1/5 : FROM openjdk:17-jre-slim
#  ---> Pulling image
# Step 2/5 : WORKDIR /app
#  ---> Running in container...
# Step 3/5 : COPY HelloApp.class .
# Step 4/5 : EXPOSE 8080
# Step 5/5 : CMD ["java", "HelloApp"]
# ---> Successfully built abc123def456
# ---> Successfully tagged hello-app:1.0

# Step 5: Run container
$ docker run hello-app:1.0

# Output:
# Hello from Docker!
# ✅ Success!

# Step 6: Verify
$ docker images
# REPOSITORY  TAG    IMAGE ID       SIZE
# hello-app   1.0    abc123def456   180MB

$ docker ps -a
# CONTAINER  STATUS          NAMES
# def456...  Exited (0)      hello-app
```

---

## 11. Docker Compose Cheat Sheet with Visuals

```
docker-compose.yml
│
├── version: '3.8'
│   └─ File format version
│
├── services:
│   │
│   ├── customer-service:
│   │   ├── build: ./services/customer-service
│   │   │   └─ Build from Dockerfile here
│   │   ├── container_name: customer-service
│   │   │   └─ Name this container
│   │   ├── ports:
│   │   │   └─ "8081:8081"
│   │   │       └─ Host:Container port mapping
│   │   ├── depends_on:
│   │   │   └─ config-server, discovery-server
│   │   │       └─ These must start first
│   │   └── environment:
│   │       └─ Environment variables for container
│   │
│   └── kafka:
│       ├── image: confluentinc/cp-kafka:7.3.2
│       │   └─ Use pre-built image
│       ├── ports:
│       │   └─ "9092:9092"
│       └── environment:
│           └─ Kafka configuration
│
├── volumes:
│   └─ Define named volumes
│       └─ prometheus-data, grafana-data, etc.
│
└── networks: (Optional)
    └─ Custom networks for services

Common docker-compose commands:
┌────────────────────────────────────────┐
│ $ docker-compose up -d                 │
│   └─ Start all services (background)   │
│                                        │
│ $ docker-compose up                    │
│   └─ Start all services (foreground)   │
│       See logs in terminal              │
│                                        │
│ $ docker-compose down                  │
│   └─ Stop all services                 │
│       Keep volumes (data safe)         │
│                                        │
│ $ docker-compose ps                    │
│   └─ Show running services             │
│                                        │
│ $ docker-compose logs -f               │
│   └─ Follow logs (like tail -f)        │
│                                        │
│ $ docker-compose build                 │
│   └─ Rebuild all images                │
│                                        │
│ $ docker-compose exec service bash     │
│   └─ Run bash inside running service   │
│                                        │
│ $ docker-compose restart service       │
│   └─ Restart specific service          │
└────────────────────────────────────────┘
```

---

## 12. Troubleshooting Decision Tree

```
Something went wrong? Follow this tree:

Is the container running?
│
├─ NO: docker ps (shows nothing)
│  │
│  └─ Run: docker logs container_id
│     │
│     ├─ Error in startup: Check application code
│     ├─ Port conflict: docker ps -a (find blocker)
│     ├─ Out of memory: docker system df
│     └─ Dependency missing: Check depends_on
│
└─ YES: docker ps (shows container)
   │
   └─ Can you reach it?
      │
      ├─ NO: localhost:8080 doesn't work
      │  │
      │  └─ Check port mapping:
      │     docker port container_id
      │     Should show: 8080/tcp → 0.0.0.0:8080
      │
      └─ YES: Connects but app broken
         │
         └─ Check logs:
            docker logs -f container_id
            │
            ├─ "Connection refused": Check service URLs
            ├─ "Out of memory": Increase memory
            ├─ "Authentication failed": Check credentials
            └─ "Timeout": Check networking
```

---

## Summary of Visuals

✅ Container architecture and layering
✅ Image vs Container difference
✅ Data flow through microservices
✅ Volume persistence mechanism
✅ Multi-stage build optimization
✅ Service discovery and registration
✅ Port mapping and networking
✅ Container lifecycle states
✅ Practical build example
✅ Docker Compose configuration
✅ Troubleshooting decision tree

---

## 🎯 Key Takeaways

1. **Containers are lightweight** - Share OS kernel, not full VMs
2. **Images are blueprints** - Templates for creating containers
3. **Services communicate via names** - Docker DNS resolves them
4. **Ports need mapping** - Host port → Container port
5. **Volumes persist data** - Survives container restart
6. **Multi-stage builds optimize** - Smaller final images
7. **Docker Compose orchestrates** - Multiple containers as one system
8. **Logging helps debugging** - Always check `docker logs`

---

## 🚀 Next Steps

Ready to go even deeper? Move to:
- **DOCKER_ZERO_TO_HERO.md** - Complete masterclass
- Learn Kubernetes for production
- Advanced networking and security
- Docker registry and CI/CD integration

---

**Remember:** These visuals represent real-world Docker concepts. Practice them step-by-step! 🎓

