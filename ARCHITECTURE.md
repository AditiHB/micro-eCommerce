# Micro E-Commerce Microservices Architecture

## 📚 Table of Contents
1. [System Overview](#system-overview)
2. [Architecture Layers](#architecture-layers)
3. [Core Design Patterns](#core-design-patterns)
4. [Service Communication](#service-communication)
5. [Data Management](#data-management)
6. [Observability Stack](#observability-stack)
7. [Security Architecture](#security-architecture)
8. [Deployment Architecture](#deployment-architecture)

---

## System Overview

This is a **production-grade microservices architecture** for an e-commerce platform built with Spring Boot 3.x. It demonstrates enterprise-level patterns for scalability, resilience, observability, and security.

### Key Characteristics
- **Distributed System**: 5 independent microservices communicating via events and APIs
- **Event-Driven**: Asynchronous processing using Apache Kafka
- **Cloud-Native**: Containerized with Kubernetes deployment
- **Observable**: Comprehensive monitoring with Prometheus/Grafana
- **Secure**: JWT authentication, Spring Security, encrypted data
- **Resilient**: Circuit breakers, retries, fallbacks, rate limiting

### Services Landscape
```
┌─────────────────────────────────────────────────────────┐
│                    API Gateway (Port 8080)              │
│         Spring Cloud Gateway + Rate Limiting            │
│                                                         │
│  ├─ JWT Authentication  ├─ Rate Limiting (1000 req/min)│
│  ├─ Request Routing     └─ Circuit Breaker             │
└─────────────────────────────────────────────────────────┘
        │           │              │              │
        ▼           ▼              ▼              ▼
┌──────────────┐ ┌─────────────┐ ┌──────────────┐ ┌──────────────┐
│   Customer   │ │    Order    │ │  Payment     │ │ Inventory    │
│   Service    │ │   Service   │ │  Service     │ │ Service      │
│  (8081)      │ │  (8083)     │ │  (8084)      │ │ (8082)       │
└──────────────┘ └─────────────┘ └──────────────┘ └──────────────┘
        │           │              │              │
        └───────────┴──────────────┴──────────────┘
                    │
        ┌───────────▼────────────┐
        │   Apache Kafka (9092)  │
        │   Event Broker         │
        └───────────┬────────────┘
                    │
        ┌───────────▼──────────────┐
        │  Event Sourcing Store    │
        │  Audit & Compliance      │
        └──────────────────────────┘
```

---

## Architecture Layers

### 1. **API Layer (Port 8080)**
- **Spring Cloud Gateway**: Central entry point for all client requests
- **Rate Limiting**: Token bucket algorithm (Redis-backed)
- **Authentication**: JWT validation at gateway level
- **Request Routing**: Dynamic routing to backend services
- **Load Balancing**: Built-in load balancing for microservices

**Technology Stack:**
- Spring Cloud Gateway 4.x
- Spring Security 6.x
- Redis for rate limiting state

### 2. **Microservices Layer (Ports 8081-8084)**

#### Customer Service (Port 8081)
- User registration and profile management
- Customer data storage
- Integration with Order Service

#### Order Service (Port 8083)
- Order creation and tracking
- Order-to-payment orchestration
- Inventory reservation coordination
- Event sourcing for audit trail

#### Payment Service (Port 8084)
- Payment processing and validation
- Transaction tracking
- Integration with Order Service

#### Inventory Service (Port 8082)
- Product catalog management
- Stock level management
- Reservation handling
- Low-stock alerts

**Common Features Across Services:**
- Spring Data JPA for persistence
- H2 database (development) / PostgreSQL (production)
- Liquibase for schema versioning
- Spring Security for authorization
- Redis caching with TTL: 10 minutes

### 3. **Data Layer**

#### Relational Database
- **Development**: H2 (in-memory)
- **Production**: PostgreSQL (recommended)
- **Schema Management**: Liquibase 4.x with versioned migrations
- **Connection Pooling**: HikariCP with optimized settings

#### Cache Layer
- **Technology**: Redis 7.x
- **Strategy**: Distributed cache with service-specific key prefixes
- **TTL**: 600 seconds (10 minutes)
- **Pool Configuration**:
  - Max Active: 8 connections
  - Max Idle: 8 connections
  - Min Idle: 0 connections
  - Timeout: 2000ms

#### Event Store
- **Technology**: Kafka topics with JSON serialization
- **Purpose**: Event sourcing for audit trail
- **Retention**: Configurable based on compliance requirements

### 4. **Message Broker Layer (Kafka)**

**Topics:**
```
- order.created          → OrderCreatedEvent
- order.confirmed        → OrderConfirmedEvent
- order.cancelled        → OrderCancelledEvent
- payment.processed      → PaymentProcessedEvent
- payment.failed         → PaymentFailedEvent
- inventory.reserved     → InventoryReservedEvent
- inventory.released     → InventoryReleasedEvent
- customer.created       → CustomerCreatedEvent
- event.dlq              → Dead Letter Queue
```

**Producer Services:**
- Order Service publishes order-related events
- Payment Service publishes payment events
- Inventory Service publishes inventory events
- Customer Service publishes customer events

**Consumer Services:**
- Cross-service event listeners
- Event sourcing persistence
- Monitoring and alerting
- Business metrics collection

### 5. **Observability Layer**

#### Logging
- **Framework**: Logback with Logstash encoder
- **Format**: Structured JSON for ELK Stack
- **Components**:
  - Console appender (development)
  - File appender (production)
  - Integration with ELK Stack

#### Metrics
- **Framework**: Micrometer 1.x
- **Export**: Prometheus format
- **Business Metrics**:
  - Orders per hour
  - Revenue tracking
  - Customer growth
  - Checkout duration (p50, p95, p99)
  - Payment processing duration

- **Technical Metrics**:
  - API response times
  - Error rates
  - Authentication failures
  - Cache hit/miss rates
  - Database query performance

#### Distributed Tracing
- **Framework**: Micrometer Tracing + Brave
- **Propagation**: X-Trace-ID headers
- **Sampling**: 100% (1.0 probability)
- **Purpose**: End-to-end request tracking

#### Monitoring & Alerting
- **Prometheus**: Metrics scraping and aggregation
- **Grafana**: Dashboard visualization
- **Alertmanager**: Alert routing and notifications
- **Dashboards**:
  - Service Health Dashboard
  - Business Metrics Dashboard
  - Performance Dashboard
  - Error Rate Dashboard

---

## Core Design Patterns

### 1. **Event-Driven Architecture**
**Pattern**: Pub-Sub with Kafka broker

**Workflow:**
```
Order Service creates order
        ↓
Publishes OrderCreatedEvent to Kafka
        ↓
Event Sourcing Store persists event
        ↓
Event Listeners process event
        ├─ Payment Service: Initiate payment
        ├─ Inventory Service: Reserve inventory
        └─ Monitoring: Update metrics
```

**Benefits:**
- Loose coupling between services
- Asynchronous processing
- Built-in audit trail
- Replay capability
- Scalability

### 2. **Event Sourcing**
**Pattern**: Immutable event log as single source of truth

**Implementation:**
- All domain events persisted in Kafka topics
- Event Store provides audit trail
- Current state derived from event stream
- Rollback capability

**Use Cases:**
- Complete audit trail for compliance
- Debugging and troubleshooting
- Temporal queries (state at any point in time)
- Event replay for data recovery

### 3. **CQRS (Command Query Responsibility Segregation)**
**Pattern**: Separate models for write and read operations

**Write Side:**
- Domain commands trigger events
- Events update event store
- Synchronous immediate response

**Read Side:**
- Materialized views from event stream
- Redis cache for fast queries
- Asynchronous updates

### 4. **Circuit Breaker Pattern**
**Pattern**: Resilience via failure detection and graceful degradation

**Implementation:**
- Spring Cloud Circuit Breaker
- States: CLOSED → OPEN → HALF_OPEN → CLOSED
- Fallback responses when service unavailable

**Configuration:**
- Failure threshold: 50%
- Sliding window size: 5 requests
- Wait duration in open state: 10 seconds

### 5. **Saga Pattern (Distributed Transactions)**
**Pattern**: Coordinate long-running transactions across services

**Order Saga:**
```
1. Create Order (Order Service)
2. Reserve Inventory (Inventory Service)
3. Process Payment (Payment Service)
4. Confirm Order (Order Service)

On failure: Compensating transactions (rollback)
```

**Benefits:**
- No distributed locking needed
- Better availability
- Clear compensation logic
- Auditable workflow

---

## Service Communication

### 1. **Synchronous (REST)**
- API Gateway routes HTTP requests
- Services communicate via REST APIs
- Rate limiting at gateway level
- Timeout: 30 seconds
- Retry: 3 attempts with exponential backoff

### 2. **Asynchronous (Event-Driven)**
- Kafka producer: Service publishing events
- Kafka consumer: Service processing events
- Dead Letter Queue for failures
- Manual retry mechanism

### 3. **Service Discovery**
- **Technology**: Eureka Server
- **Configuration**: `eureka.client.service-url.defaultZone`
- **Health checks**: Spring Boot Actuator endpoints
- **Automatic deregistration**: 90 seconds without heartbeat

---

## Data Management

### 1. **Database Per Service Pattern**
Each service has its own database:
- **Customer Service**: `customer_db`
- **Order Service**: `order_db`
- **Payment Service**: `payment_db`
- **Inventory Service**: `inventory_db`

**Benefits:**
- Service independence
- Scaling at service level
- Technology choice per service
- Failure isolation

### 2. **Schema Versioning (Liquibase)**

**Migration Strategy:**
```
0001_initial_schema.xml       → Base tables
0002_add_audit_tables.xml     → Audit logging
0003_add_event_store.xml      → Event sourcing
0004_add_indexes.xml          → Performance
0005_add_partitions.xml       → Scalability
```

**Key Features:**
- Version control for schema changes
- Rollback capability
- Pre/post-condition checks
- Automatically run on startup

### 3. **Caching Strategy**

**Cache Types:**
1. **Distributed Cache (Redis)**
   - Shared across service instances
   - Reduced database load
   - Consistent data across instances

2. **Local Cache (In-Memory)**
   - Fastest access
   - Limited capacity
   - Potential inconsistency

**Cache Invalidation:**
- TTL: 10 minutes
- Event-driven invalidation
- Manual eviction on write

---

## Observability Stack

### 1. **Logs → ELK Stack**
```
Application Logs (JSON format)
        ↓
Logstash (parsing & enrichment)
        ↓
Elasticsearch (storage & indexing)
        ↓
Kibana (visualization & analysis)
```

### 2. **Metrics → Prometheus → Grafana**
```
Micrometer (collection)
        ↓
Prometheus (scraping: every 15s)
        ↓
Grafana (visualization & alerting)
```

### 3. **Traces → Distributed Tracing**
```
Request arrives
        ↓
X-Trace-ID generated/propagated
        ↓
Services append span information
        ↓
Trace context forwarded to next service
        ↓
Complete trace reconstructed for analysis
```

### 4. **Health Checks**
- **Liveness**: Is the service alive? (restart on failure)
- **Readiness**: Can the service accept traffic? (remove from load balancer)
- **Custom health indicators**: Redis cache health, database connectivity

---

## Security Architecture

### 1. **Authentication (JWT)**
```
Client Login Request
        ↓
Verify credentials
        ↓
Generate JWT token (HS256)
        ↓
Return token to client
        ↓
Client includes token in Authorization header
        ↓
API Gateway validates token
        ↓
Token propagated to microservices
```

**Token Structure:**
- Header: `{"alg":"HS256","typ":"JWT"}`
- Payload: `{"sub":"user_id","exp":timestamp,"iat":timestamp}`
- Signature: HMAC(secret)

**Token Expiration:**
- Default: 24 hours (86400000 milliseconds)
- Configurable per environment

### 2. **Authorization (Spring Security)**
- Role-based access control (RBAC)
- Method-level security annotations
- Service-to-service security

### 3. **Data Security**
- Encryption at rest (database level)
- Encryption in transit (HTTPS/TLS)
- Sensitive data masking in logs
- Credential storage in environment variables

### 4. **API Gateway Security**
- JWT validation at gateway
- Rate limiting per client
- CORS configuration
- Request/response logging (audit trail)

---

## Deployment Architecture

### 1. **Containerization (Docker)**

**Multi-Stage Build:**
```
Stage 1: Build (Maven compile)
        ↓
Stage 2: Runtime (JDK runtime image)
        ↓
Final image: Lightweight & optimized
```

**Image Size Optimization:**
- Base image: eclipse-temurin:21-jre-alpine (~180MB)
- Multi-stage build reduces final size
- Only runtime dependencies included

### 2. **Orchestration (Kubernetes)**

**K8s Resources:**
```
Namespace: ecommerce
├── Deployments (5)
│   ├── api-gateway
│   ├── customer-service
│   ├── order-service
│   ├── payment-service
│   └── inventory-service
├── StatefulSets (2)
│   ├── redis
│   └── postgres
├── Services (7)
│   ├── api-gateway (LoadBalancer)
│   ├── customer-service (ClusterIP)
│   ├── order-service (ClusterIP)
│   ├── payment-service (ClusterIP)
│   ├── inventory-service (ClusterIP)
│   ├── redis (ClusterIP)
│   └── postgres (ClusterIP)
├── ConfigMaps
│   └── application-config
├── Secrets
│   ├── jwt-secret
│   ├── db-credentials
│   └── redis-password
└── HPA (Horizontal Pod Autoscaler)
    └── service-autoscaler
```

### 3. **Helm Charts**
Templated K8s deployment for easy customization:
```
helm install micro-ecommerce ./helm \
  -n ecommerce \
  --create-namespace \
  --values values.yaml
```

### 4. **CI/CD Pipeline**

**Stages:**
1. **Build**: Maven compile & test
2. **Quality**: SonarQube code analysis
3. **Security**: Dependency vulnerability scanning
4. **Containerize**: Docker image build
5. **Push**: Registry push (Docker Hub / ECR)
6. **Deploy**: K8s manifest apply
7. **Test**: Smoke tests

---

## Technology Stack Summary

| Layer | Technology | Version |
|-------|-----------|---------|
| **Framework** | Spring Boot | 3.x |
| **Web** | Spring MVC + Spring Cloud Gateway | 4.x |
| **Security** | Spring Security + JWT (JJWT) | 6.x / 0.12.x |
| **Data** | Spring Data JPA + Hibernate | 6.x |
| **Cache** | Spring Data Redis + Jedis | 7.x |
| **Events** | Apache Kafka | 3.x |
| **Logging** | Logback + Logstash | 7.4+ |
| **Metrics** | Micrometer | 1.x |
| **Monitoring** | Prometheus + Grafana | 2.45+ / 10.x |
| **Containers** | Docker | 24.x |
| **Orchestration** | Kubernetes | 1.27+ |
| **IaC** | Helm | 3.x |
| **DB Versioning** | Liquibase | 4.x |
| **Database** | H2 (dev) / PostgreSQL (prod) | 2.x |
| **Testing** | JUnit 5 + Mockito | 5.x / 5.x |

---

## Data Flow Examples

### Example 1: Order Creation Flow
```
1. Client POST /api/orders → API Gateway
2. Gateway validates JWT token
3. Gateway rate limiting check
4. Request routed to Order Service
5. Order Service validates order
6. Order created in database
7. OrderCreatedEvent published to Kafka
8. Event Sourcing Store persists event
9. Event Listeners (Payment, Inventory) consume event
10. Payment Service initiates payment
11. Inventory Service reserves stock
12. Response returned to client
```

### Example 2: Payment Processing Flow
```
1. PaymentService receives PaymentProcessedEvent from Kafka
2. Payment validated against Payment Gateway
3. Transaction recorded in database
4. PaymentProcessedEvent published
5. Order Service consumes event
6. Order status updated to "PAID"
7. OrderConfirmedEvent published
8. Inventory Service confirms reservation
9. Metrics updated (revenue, transaction count)
10. Logs written for audit trail
```

### Example 3: Cache Update Flow
```
1. Customer updates profile → Customer Service
2. Update record in database
3. Publish CustomerUpdatedEvent
4. @CacheEvict invalidates cache entry
5. Next GET request triggers cache refresh
6. New data cached for 10 minutes
7. Subsequent requests served from cache
8. On TTL expiry, cache refreshed
```

---

## Key Learning Concepts

### 1. **Eventual Consistency**
- Services don't have real-time data
- Data propagates asynchronously
- Acceptable for most business scenarios
- Requires idempotent operations

### 2. **Distributed Transactions**
- Two-phase commit not recommended
- Saga pattern for compensation
- Event sourcing for rollback
- Accept temporary inconsistency

### 3. **Service Independence**
- Services can scale independently
- Technology choices per service
- Deployment independent
- Failure isolation

### 4. **Observability-First Design**
- Logs for debugging
- Metrics for monitoring
- Traces for performance analysis
- Health checks for automation

### 5. **Resilience Patterns**
- Timeouts prevent hanging
- Retries with backoff
- Circuit breaker stops cascade failures
- Fallbacks provide degraded service

---

## Next Steps for Learning

1. **Phase 14**: Infrastructure as Code with Terraform
2. **Phase 15**: Advanced security (OAuth2, RBAC, Secret management)
3. **Phase 16**: Disaster recovery & backup strategies
4. **Phase 17**: High availability & auto-scaling patterns
5. **Service Mesh**: Istio/Linkerd for advanced traffic management

