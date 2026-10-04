# Complete Phase Implementation Guide

## 📖 Learning Path Through All 13 Phases

This document provides a comprehensive walkthrough of every phase, explaining the **why**, **what**, and **how** of each implementation.

---

## Phase 1: Java Standards & Code Quality

### Concept: Clean Code Foundation
Building professional-grade Java code following SOLID principles and industry standards.

### What Was Implemented
- **Package Structure**: Organized by domain (customer, order, payment, inventory)
- **Naming Conventions**: Clear, descriptive names for classes, methods, variables
- **Code Organization**: Separation of concerns (Controller, Service, Repository, Entity)
- **Documentation**: Javadoc for public APIs

### Technologies
- Java 17
- Maven 3.9+
- Spring Framework conventions

### Learning Points
1. **SOLID Principles**:
   - Single Responsibility: Each class has one reason to change
   - Open/Closed: Open for extension, closed for modification
   - Liskov Substitution: Derived classes can substitute base classes
   - Interface Segregation: Clients depend on specific interfaces
   - Dependency Inversion: Depend on abstractions, not concretions

2. **Project Structure**:
   ```
   project/
   ├── common/              # Shared components
   ├── services/
   │   ├── customer-service/
   │   ├── order-service/
   │   ├── payment-service/
   │   └── inventory-service/
   └── pom.xml             # Maven configuration
   ```

3. **Maven Best Practices**:
   - Dependency management in parent pom
   - Build plugins configuration
   - Profile-based builds (dev, test, prod)

---

## Phase 2: Integration Tests & API Documentation

### Concept: Quality Assurance & Developer Experience
Automated testing and API documentation for reliability and usability.

### What Was Implemented
- **Integration Tests**: JUnit 5 + Mockito for service layer testing
- **Test Profiles**: Separate `application-test.yml` for test environment
- **API Documentation**: OpenAPI 3.0 with Swagger annotations
- **Test Data**: Fixtures and factories for test setup

### Technologies
- JUnit 5
- Mockito 5.x
- Testcontainers (optional for database testing)
- SpringBoot Test framework
- Springdoc OpenAPI

### Learning Points
1. **Integration Testing**:
   ```java
   @SpringBootTest                    // Load full Spring context
   @ActiveProfiles("test")            // Use test configuration
   class OrderServiceTest {
       @Autowired
       OrderService orderService;     // Inject service
       
       @Test
       void testOrderCreation() {
           // Arrange, Act, Assert pattern
       }
   }
   ```

2. **Test Profiles**:
   ```yaml
   # application-test.yml
   spring:
     datasource:
       url: jdbc:h2:mem:testdb      # In-memory H2
       driverClassName: org.h2.Driver
     jpa:
       hibernate:
         ddl-auto: create-drop        # Create fresh schema for each test
   ```

3. **API Documentation**:
   ```java
   @RestController
   @RequestMapping("/api/customers")
   @Tag(name = "Customer Management")  // Group in Swagger UI
   public class CustomerController {
       
       @GetMapping
       @Operation(summary = "Get all customers")
       @ApiResponses(value = {
           @ApiResponse(responseCode = "200", description = "Success"),
           @ApiResponse(responseCode = "500", description = "Server error")
       })
       public ResponseEntity<List<Customer>> getAll() {
           // Implementation
       }
   }
   ```

---

## Phase 3: Spring Security & JWT Authentication

### Concept: Secure Access Control
Implementing authentication and authorization for microservices.

### What Was Implemented
- **JWT (JSON Web Tokens)**: Stateless authentication
- **Spring Security**: Authorization and request filtering
- **Password Encoding**: BCrypt for secure password storage
- **Role-Based Access Control (RBAC)**: Different permissions per role

### Technologies
- Spring Security 6.x
- JJWT (Java JWT library) 0.12.x
- BCrypt password encoder

### Learning Points

1. **JWT Concept**:
   ```
   Token Structure: Header.Payload.Signature
   
   Header:     {"alg":"HS256","typ":"JWT"}
   Payload:    {"sub":"user123","roles":["ADMIN"],"exp":1234567890}
   Signature:  HMAC-SHA256(header.payload, secret)
   ```

2. **JWT Flow**:
   ```
   Login Request
       ↓
   Verify Credentials
       ↓
   Generate Token (signed with secret)
       ↓
   Return Token to Client
       ↓
   Client sends token in Authorization header: "Bearer <token>"
       ↓
   Server validates signature & expiration
       ↓
   Grant/Deny access
   ```

3. **Spring Security Configuration**:
   ```java
   @Configuration
   @EnableWebSecurity
   public class SecurityConfig {
       
       @Bean
       public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
           http
               .csrf().disable()                           // Disable CSRF (stateless API)
               .exceptionHandling()
               .authenticationEntryPoint(...)              // Handle 401 Unauthorized
               .and()
               .sessionManagement()
               .sessionCreationPolicy(STATELESS)           // No server-side sessions
               .and()
               .authorizeRequests()
               .antMatchers("/login").permitAll()          // Public endpoint
               .antMatchers("/api/**").authenticated()     // Require authentication
               .anyRequest().permitAll();
           
           http.addFilterBefore(
               jwtAuthenticationFilter(),                  // Custom JWT filter
               UsernamePasswordAuthenticationFilter.class
           );
           
           return http.build();
       }
   }
   ```

4. **Custom JWT Filter**:
   ```java
   // Intercepts every request
   // Extracts JWT from Authorization header
   // Validates signature and expiration
   // Sets authentication context for Spring Security
   ```

---

## Phase 4: Distributed Tracing, Caching & Metrics

### Concept: Performance & Observability
Monitoring system behavior and improving response times.

### What Was Implemented
- **Caching Layer**: In-memory cache with ConcurrentMapCacheManager
- **Metrics Collection**: Micrometer for gathering performance data
- **Distributed Tracing**: Brave integration for request tracing
- **Application Metrics**: Custom counters and timers

### Technologies
- Micrometer 1.x
- Brave (distributed tracing)
- Spring Cache abstraction
- Spring Boot Actuator

### Learning Points

1. **Caching Strategy**:
   ```java
   @Service
   public class CustomerService {
       
       // Cache customer lookups (key: customerId)
       @Cacheable(value = "customers", key = "#id")
       public Customer getCustomer(Long id) {
           // Database query happens only if not in cache
           return customerRepository.findById(id);
       }
       
       // Invalidate cache when updating
       @CacheEvict(value = "customers", key = "#id")
       public Customer updateCustomer(Long id, CustomerDTO dto) {
           // Update logic
       }
       
       // Clear entire cache
       @CacheEvict(value = "customers", allEntries = true)
       public void refreshAllCustomers() {
           // Full refresh
       }
   }
   ```

2. **Metrics Recording**:
   ```java
   @Component
   public class ApplicationMetrics {
       
       // Counter: incremented each time operation occurs
       private Counter customerCreated;
       
       // Timer: tracks operation duration
       private Timer customerCreationTimer;
       
       public void recordCustomerCreated() {
           customerCreated.increment();
           customerCreationTimer.record(() -> {
               // Execution time measured
           });
       }
   }
   ```

3. **Actuator Endpoints**:
   ```
   GET /actuator/health
   {
       "status": "UP",
       "components": {
           "db": {"status": "UP"},
           "redis": {"status": "UP"}
       }
   }
   
   GET /actuator/metrics
   [
       "jvm.memory.used",
       "application.customers.created",
       "application.orders.processing.time"
   ]
   
   GET /actuator/prometheus
   # Prometheus format for scraping
   ```

4. **Distributed Tracing**:
   ```
   Request arrives with/without X-Trace-ID
       ↓
   If no trace ID, generate new UUID
       ↓
   Include trace ID in all logs & downstream calls
       ↓
   Services append span information
       ↓
   Complete request trace assembled
       ↓
   Analyze performance & identify bottlenecks
   ```

---

## Phase 5+7: Redis Caching & Enhanced Observability

### Concept: Distributed Caching & Enterprise Logging
Scaling the cache layer and implementing comprehensive observability.

### What Was Implemented
- **Distributed Redis Cache**: Shared across service instances
- **Structured JSON Logging**: Logback + Logstash encoder for ELK Stack
- **Enhanced Health Indicators**: Custom cache health checks
- **Request/Response Logging**: Full HTTP audit trail

### Technologies
- Redis 7.x
- Jedis client
- Logback 1.x
- Logstash encoder 7.4+
- Spring Data Redis

### Learning Points

1. **Distributed Caching**:
   ```
   Before Redis:
   ┌────────────┐       ┌────────────┐
   │ Service 1  │       │ Service 2  │
   │ ┌────────┐ │       │ ┌────────┐ │
   │ │ Cache  │ │       │ │ Cache  │ │  <- Different data!
   │ └────────┘ │       │ └────────┘ │
   └────────────┘       └────────────┘
   
   After Redis:
   ┌────────────┐       ┌────────────┐
   │ Service 1  │       │ Service 2  │
   └────────────┘       └────────────┘
          │                  │
          └──────┬───────────┘
                 ▼
          ┌─────────────┐
          │    Redis    │  <- Single source of truth
          │   Cache     │
          └─────────────┘
   ```

2. **Redis Configuration**:
   ```java
   @Configuration
   public class RedisConfig {
       
       @Bean
       public LettuceConnectionFactory connectionFactory() {
           // Configure Redis connection
           // Connection pooling settings
           // Serialization format (JSON)
       }
       
       @Bean
       public RedisCacheManager cacheManager(
               LettuceConnectionFactory connectionFactory) {
           // Configure cache TTL: 10 minutes
           // Service-specific key prefixes
           // Eviction policy
       }
   }
   ```

3. **Structured JSON Logging**:
   ```json
   {
       "@timestamp": "2026-09-27T08:30:00.000Z",
       "level": "INFO",
       "message": "Order created successfully",
       "logger": "OrderService",
       "trace_id": "abc123def456",
       "user_id": "user_123",
       "order_id": "order_456",
       "response_time_ms": 145,
       "service": "order-service",
       "environment": "production"
   }
   ```

4. **Custom Health Indicator**:
   ```java
   @Component
   public class CacheHealthIndicator extends AbstractHealthIndicator {
       
       // Checks Redis connectivity
       // Validates cache is operational
       // Returns detailed health status
       
       @Override
       protected void doHealthCheck(Health.Builder builder) {
           try {
               // Test set/get operation
               redisTemplate.opsForValue().set("health", "OK");
               String value = redisTemplate.opsForValue().get("health");
               
               if ("OK".equals(value)) {
                   builder.up().withDetail("redis", "connected");
               } else {
                   builder.down().withDetail("redis", "data inconsistent");
               }
           } catch (Exception e) {
               builder.down().withDetail("redis", "connection failed");
           }
       }
   }
   ```

---

## Phase 6: Event-Driven Architecture

### Concept: Asynchronous Processing & Audit Trail
Building scalable, loosely-coupled services through events.

### What Was Implemented
- **Kafka Integration**: Message broker for event streaming
- **Event Sourcing**: Complete audit trail of all operations
- **Event Listeners**: Asynchronous event consumption
- **Dead Letter Queue**: Handling failed event processing
- **Event Models**: Domain events with correlation tracking

### Technologies
- Apache Kafka 3.x
- Spring Kafka
- Spring Cloud Stream (optional)
- Avro/JSON for serialization

### Learning Points

1. **Event-Driven Flow**:
   ```
   Order Service
       │
       ├─ Create Order
       ├─ Save to database
       ├─ Publish OrderCreatedEvent to Kafka
       └─ Return response to client (before event processing!)
   
   Event Stream (Kafka)
       │
       ├─ OrderCreatedEvent persisted
       ├─ Consumed by multiple listeners
       │
   Inventory Service
       ├─ Reserves stock
       ├─ Publishes InventoryReservedEvent
       │
   Payment Service
       ├─ Initiates payment
       ├─ Publishes PaymentProcessedEvent
       │
   Order Service (again)
       ├─ Updates order status to CONFIRMED
   ```

2. **Event Sourcing Concept**:
   ```
   Traditional Approach:
   ┌─────────────────┐
   │  Order Table    │
   ├─────────────────┤
   │ id: 1           │
   │ status: PAID    │  <- Current state only
   │ total: $100.00  │
   └─────────────────┘
   
   Event Sourcing Approach:
   ┌──────────────────────────┐
   │    Event Log             │
   ├──────────────────────────┤
   │ OrderCreatedEvent        │
   │ {id: 1, total: $100}     │
   │                          │
   │ InventoryReservedEvent   │
   │ {orderId: 1, items: 3}   │
   │                          │
   │ PaymentProcessedEvent    │
   │ {orderId: 1, amount:100} │
   │                          │
   │ OrderConfirmedEvent      │
   │ {orderId: 1}             │
   └──────────────────────────┘
        ↓ Replay all events
   Current state reconstructed
   ```

3. **Kafka Topic Structure**:
   ```yaml
   Topics:
   - order.created              # New order created
   - order.confirmed            # Order confirmed after payment
   - order.cancelled            # Order cancelled
   - payment.processed          # Payment successful
   - payment.failed             # Payment failed
   - inventory.reserved         # Items reserved
   - inventory.released         # Reservation cancelled
   - customer.created           # New customer registered
   
   Configuration:
   - Partitions: 3              # Parallel processing
   - Replication Factor: 2      # Fault tolerance
   - Retention: 7 days          # Data retention
   ```

4. **Event Class Structure**:
   ```java
   public abstract class DomainEvent {
       private String eventId;              // Unique event ID
       private String correlationId;        // Trace across requests
       private LocalDateTime timestamp;     // When event occurred
       private String aggregateId;          // Related entity ID
   }
   
   public class OrderCreatedEvent extends DomainEvent {
       private Long orderId;
       private Long customerId;
       private BigDecimal totalAmount;
       private List<OrderItem> items;
   }
   ```

5. **Event Listener Pattern**:
   ```java
   @Service
   public class OrderEventListener {
       
       // Listens to specific Kafka topic
       @KafkaListener(
           topics = "order.created",
           groupId = "inventory-service-group"
       )
       public void onOrderCreated(OrderCreatedEvent event) {
           // Process event asynchronously
           // Publish InventoryReservedEvent
           // Update inventory database
       }
   }
   ```

---

## Phase 8: API Gateway & Rate Limiting

### Concept: Single Entry Point & Traffic Control
Centralizing API management and protecting against abuse.

### What Was Implemented
- **Spring Cloud Gateway**: Intelligent routing
- **Rate Limiting**: Token bucket algorithm
- **JWT Validation**: Authentication at gateway
- **Circuit Breaker**: Fault tolerance
- **Request Logging**: Complete audit trail

### Technologies
- Spring Cloud Gateway 4.x
- Redis for rate limiting
- Spring Cloud Hystrix (circuit breaker)

### Learning Points

1. **Gateway Architecture**:
   ```
   Client Request
       ↓
   API Gateway (Port 8080)
       ├─ Validate JWT token
       ├─ Check rate limits
       ├─ Check circuit breaker state
       ├─ Route to appropriate service
       ├─ Add correlation ID header
       └─ Log request
   
   Service Response
       ↓
   Gateway
       ├─ Add tracing headers
       ├─ Log response
       └─ Return to client
   ```

2. **Rate Limiting Algorithm (Token Bucket)**:
   ```
   ┌──────────────────────────┐
   │    Token Bucket          │
   │                          │
   │  Capacity: 1000 tokens   │
   │  Refill rate: 16.67/sec  │
   │  Current: 950 tokens     │
   └──────────────────────────┘
   
   Request arrives
       ↓
   Check available tokens
       ├─ If tokens ≥ 1:
       │   ├─ Consume 1 token
       │   └─ Allow request (200 OK)
       └─ If tokens < 1:
           ├─ Refill tokens at rate
           └─ Reject with 429 (Too Many Requests)
   ```

3. **Gateway Routes Configuration**:
   ```yaml
   spring:
     cloud:
       gateway:
         routes:
         - id: customer-service
           uri: http://customer-service:8081
           predicates:
           - Path=/api/customers/**
           filters:
           - name: CircuitBreaker
             args:
               name: customerService
               fallbackUri: forward:/customer-fallback
         
         - id: order-service
           uri: http://order-service:8083
           predicates:
           - Path=/api/orders/**
   ```

4. **Circuit Breaker States**:
   ```
   CLOSED (Normal Operation)
   ├─ Requests pass through
   ├─ Count failures
   └─ If failure rate > threshold → OPEN
   
   OPEN (Service Down)
   ├─ Requests blocked
   ├─ Return fallback response
   ├─ After wait duration → HALF_OPEN
   
   HALF_OPEN (Testing Recovery)
   ├─ Allow test request
   ├─ If successful → CLOSED
   └─ If failed → OPEN
   ```

---

## Phase 9: Monitoring & Alerting

### Concept: Operational Visibility
Real-time monitoring with Prometheus and alerting with Grafana.

### What Was Implemented
- **Prometheus**: Metrics collection and time-series database
- **Grafana**: Visualization and dashboarding
- **Alertmanager**: Alert routing and notifications
- **Custom Metrics**: Business and technical metrics
- **Health Checks**: Continuous monitoring

### Technologies
- Prometheus 2.45+
- Grafana 10.x
- Alertmanager 0.25+
- Micrometer Prometheus registry

### Learning Points

1. **Metrics Pipeline**:
   ```
   Application (Micrometer)
       │ Exposes metrics in Prometheus format
       ├─ /actuator/prometheus
   
   Prometheus Server
       │ Scrapes every 15 seconds
       ├─ Stores time-series data
       ├─ Evaluates alert rules
   
   Alertmanager
       │ Routes alerts
       ├─ Email notifications
       ├─ Slack messages
       ├─ PagerDuty escalations
   
   Grafana
       │ Queries Prometheus
       ├─ Visualizes dashboards
       └─ Displays alerts
   ```

2. **Prometheus Scrape Config**:
   ```yaml
   # prometheus.yml
   global:
     scrape_interval: 15s           # How often to scrape
     evaluation_interval: 15s        # Alert rule evaluation
   
   scrape_configs:
   - job_name: 'customer-service'
     static_configs:
     - targets: ['customer-service:8081']
     metrics_path: '/actuator/prometheus'
   
   - job_name: 'order-service'
     static_configs:
     - targets: ['order-service:8083']
   ```

3. **Alert Rules**:
   ```yaml
   # alert-rules.yml
   groups:
   - name: microservices
     rules:
     
     # Alert when service down for > 1 minute
     - alert: ServiceDown
       expr: up{job=~".*-service"} == 0
       for: 1m
       labels:
         severity: critical
       annotations:
         summary: "{{ $labels.job }} is down"
     
     # Alert when error rate > 5%
     - alert: HighErrorRate
       expr: |
         (
           rate(http_server_requests_seconds_count{status=~"5.."}[5m])
           /
           rate(http_server_requests_seconds_count[5m])
         ) > 0.05
       for: 5m
       labels:
         severity: warning
   ```

4. **Grafana Dashboard Queries**:
   ```
   Requests per Second:
   rate(http_server_requests_seconds_count[5m])
   
   Average Response Time:
   rate(http_server_requests_seconds_sum[5m]) / 
   rate(http_server_requests_seconds_count[5m])
   
   Error Rate:
   rate(http_server_requests_seconds_count{status=~"5.."}[5m])
   
   Cache Hit Rate:
   rate(cache_gets_hit[5m]) / 
   rate(cache_gets_total[5m])
   ```

---

## Phase 10: Kubernetes Deployment

### Concept: Container Orchestration & Infrastructure as Code
Running microservices at scale with automatic management.

### What Was Implemented
- **Docker Images**: Multi-stage builds for each service
- **K8s Manifests**: Deployments, Services, ConfigMaps, Secrets
- **StatefulSets**: Persistent Redis and database
- **Helm Charts**: Templated deployments
- **Health Probes**: Liveness and readiness checks

### Technologies
- Docker 24.x
- Kubernetes 1.27+
- Helm 3.x
- kubectl CLI

### Learning Points

1. **Container Basics**:
   ```dockerfile
   # Multi-stage build for optimization
   
   # Stage 1: Build
   FROM maven:3.9 AS builder
   WORKDIR /app
   COPY . .
   RUN mvn clean package -DskipTests
   
   # Stage 2: Runtime
   FROM eclipse-temurin:21-jre-alpine
   WORKDIR /app
   COPY --from=builder /app/target/*.jar app.jar
   EXPOSE 8081
   ENTRYPOINT ["java", "-jar", "app.jar"]
   ```

2. **K8s Deployment Manifest**:
   ```yaml
   apiVersion: apps/v1
   kind: Deployment
   metadata:
     name: customer-service
     namespace: ecommerce
   spec:
     replicas: 3                    # Run 3 instances
     selector:
       matchLabels:
         app: customer-service
     template:
       metadata:
         labels:
           app: customer-service
       spec:
         containers:
         - name: customer-service
           image: customer-service:latest
           ports:
           - containerPort: 8081
           
           # Health checks
           livenessProbe:            # Is pod alive?
             httpGet:
               path: /actuator/health/liveness
               port: 8081
             initialDelaySeconds: 30
             periodSeconds: 10
           
           readinessProbe:           # Ready for traffic?
             httpGet:
               path: /actuator/health/readiness
               port: 8081
             initialDelaySeconds: 20
             periodSeconds: 5
           
           # Resource limits
           resources:
             requests:
               memory: "256Mi"
               cpu: "250m"
             limits:
               memory: "512Mi"
               cpu: "500m"
           
           # Environment from ConfigMap
           envFrom:
           - configMapRef:
               name: app-config
           
           # Secrets
           env:
           - name: JWT_SECRET
             valueFrom:
               secretKeyRef:
                 name: jwt-secret
                 key: secret
   ```

3. **Service Discovery in K8s**:
   ```
   Inside K8s cluster:
   
   Customer Service → order-service:8083
   (DNS automatically resolves)
   
   Outside cluster:
   
   Client → api-gateway (LoadBalancer)
   → External IP: 203.0.113.45:8080
   ```

4. **Helm Chart Structure**:
   ```
   helm/
   ├── Chart.yaml              # Chart metadata
   ├── values.yaml             # Default values
   ├── templates/
   │   ├── deployment.yaml     # Service deployment
   │   ├── service.yaml        # K8s service
   │   ├── configmap.yaml      # Configuration
   │   ├── secret.yaml         # Secrets
   │   └── hpa.yaml            # Autoscaler
   ```

5. **Helm Deployment**:
   ```bash
   # Install with custom values
   helm install micro-ecommerce ./helm \
     -n ecommerce \
     --create-namespace \
     --values values-prod.yaml
   
   # Upgrade to new version
   helm upgrade micro-ecommerce ./helm \
     -n ecommerce \
     --values values-prod.yaml
   
   # Rollback if needed
   helm rollback micro-ecommerce
   ```

---

## Phase 11: Database Migrations

### Concept: Version-Controlled Schema Management
Safe, repeatable database changes that run automatically as part of application startup.

### What Was Implemented
- **Flyway Integration**: Database version control, one instance per microservice
- **Versioned SQL Migrations**: Plain `.sql` files, no XML/YAML changelog format to learn
- **Automatic Execution**: Runs on Spring Boot startup, no separate migrate command
- **Schema Versioning**: Flyway tracks every applied migration in `flyway_schema_history`
- **Multi-Database Support**: Per-vendor migration folders (H2, PostgreSQL, Oracle)

### Technologies
- Flyway Community Edition
- Plain SQL migration files

### Learning Points

1. **Flyway Concept**:
   ```
   V1__Create_Customers_Table.sql
   ├─ Create customers table
   └─ Create indexes

   V2__Insert_Sample_Customers.sql
   └─ Seed data

   V3__Create_Event_Store_Table.sql
   └─ Add event sourcing table

   Current database: Version 3 (flyway_schema_history records V1, V2, V3 as applied)
   ```

2. **Migration File Structure** (plain SQL, no custom XML format to learn):
   ```sql
   -- V1__Create_Customers_Table.sql
   CREATE TABLE customers (
       id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
       email VARCHAR(255) NOT NULL UNIQUE,
       created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
   );

   CREATE INDEX idx_customers_email ON customers(email);
   ```

3. **No Automated Rollback (Community Edition)**:
   ```bash
   # Forward: applies automatically on service startup - nothing to run manually

   # Inspect what's applied / pending for a given service
   mvn -pl services/customer-service flyway:info

   # Validate the schema history against the files on disk
   mvn -pl services/customer-service flyway:validate
   ```
   Flyway's automated rollback is a paid Teams/Enterprise feature. The open-source
   workflow is "roll forward": write a new migration (e.g. `V4__...sql`) that undoes
   or corrects the previous change, rather than reversing history. See
   [db/README.md](db/README.md) for the full picture.

4. **Idempotent, Defensive Migrations**:
   ```sql
   -- V3__Add_Status_Column.sql
   -- Flyway tracks what ran, but writing migrations defensively still helps
   -- when testing against a database that was partially set up by hand.
   ALTER TABLE orders ADD COLUMN IF NOT EXISTS status VARCHAR(20);
   ```

---

## Phase 12: CI/CD Pipeline

### Concept: Automated Testing, Building, and Deployment
Continuous integration and deployment for rapid, safe releases.

### What Was Implemented
- **GitHub Actions Workflows**: Automated pipelines
- **Build Pipeline**: Maven compile and test
- **Quality Gates**: Code analysis and coverage
- **Security Scanning**: Vulnerability detection
- **Docker Builds**: Automated image creation
- **Deployment Automation**: K8s manifest deployment

### Technologies
- GitHub Actions
- Maven
- SonarQube (optional)
- Snyk (vulnerability scanning)
- Docker Registry

### Learning Points

1. **CI/CD Pipeline Stages**:
   ```
   1. Trigger (on push/PR)
       ↓
   2. Build & Test
       ├─ Maven compile
       ├─ Unit tests
       └─ Integration tests
       ↓
   3. Quality Analysis
       ├─ Code coverage
       ├─ Code smells
       └─ Duplicate code
       ↓
   4. Security Scan
       ├─ Dependency vulnerabilities
       ├─ Secret detection
       └─ SAST analysis
       ↓
   5. Build Docker Image
       ├─ Multi-stage build
       └─ Push to registry
       ↓
   6. Deploy
       ├─ Apply K8s manifests
       └─ Verify deployment
       ↓
   7. Smoke Tests
       ├─ Health check
       ├─ Critical path tests
       └─ Performance baseline
   ```

2. **GitHub Actions Workflow**:
   ```yaml
   name: Build and Deploy
   
   on:
     push:
       branches: [main, develop]
     pull_request:
       branches: [main]
   
   jobs:
     build:
       runs-on: ubuntu-latest
       steps:
       - uses: actions/checkout@v3
       
       - name: Set up Java
         uses: actions/setup-java@v3
         with:
           java-version: '21'
           distribution: 'temurin'
       
       - name: Build with Maven
         run: mvn clean package
       
       - name: Run tests
         run: mvn test
       
       - name: Build Docker image
         run: |
           docker build -t customer-service:${{ github.sha }} .
           docker push my-registry/customer-service:${{ github.sha }}
       
       - name: Deploy to K8s
         run: |
           kubectl set image deployment/customer-service \
             customer-service=my-registry/customer-service:${{ github.sha }}
   ```

3. **Quality Gate Thresholds**:
   ```
   Code Coverage:
   - Minimum: 80%
   - Critical: 70%
   
   Code Smells:
   - Allowed: 0 blocker, 5 major
   
   Duplicated Code:
   - Maximum: 3%
   
   Security Hotspots:
   - All must be reviewed
   ```

---

## Phase 13: API Documentation & SDKs

### Concept: Developer Experience & Integration Ease
Auto-generated documentation and client libraries.

### What Was Implemented
- **OpenAPI 3.0 Spec**: Machine-readable API definition
- **Swagger UI**: Interactive API explorer
- **Client SDK Generation**: Java, Python, TypeScript clients
- **AsyncAPI Spec**: Kafka topic documentation
- **Postman Collection**: Ready-to-use API requests
- **Webhook Schema**: Event documentation

### Technologies
- Springdoc OpenAPI
- OpenAPI Generator
- AsyncAPI specification

### Learning Points

1. **OpenAPI Structure**:
   ```yaml
   openapi: 3.0.0
   info:
     title: Customer Service API
     version: 1.0.0
   
   servers:
   - url: http://localhost:8080
   
   paths:
     /api/customers:
       get:
         operationId: getAllCustomers
         summary: Get all customers
         parameters:
         - name: page
           in: query
           schema:
             type: integer
         responses:
           '200':
             description: Success
             content:
               application/json:
                 schema:
                   type: array
                   items:
                     $ref: '#/components/schemas/Customer'
   
   components:
     schemas:
       Customer:
         type: object
         properties:
           id:
             type: integer
           email:
             type: string
           name:
             type: string
   ```

2. **Swagger Annotations**:
   ```java
   @RestController
   @RequestMapping("/api/customers")
   @Tag(
       name = "Customer Management",
       description = "APIs for managing customer profiles"
   )
   public class CustomerController {
       
       @GetMapping("/{id}")
       @Operation(
           summary = "Get customer by ID",
           description = "Retrieve a specific customer profile"
       )
       @ApiResponses({
           @ApiResponse(
               responseCode = "200",
               description = "Customer found",
               content = @Content(schema = @Schema(implementation = CustomerDTO.class))
           ),
           @ApiResponse(
               responseCode = "404",
               description = "Customer not found"
           )
       })
       public ResponseEntity<CustomerDTO> getCustomer(
           @PathVariable
           @Parameter(description = "Customer ID", example = "123")
           Long id
       ) {
           // Implementation
       }
   }
   ```

3. **SDK Generation**:
   ```bash
   # Generate Java SDK
   openapi-generator-cli generate \
     -i openapi-spec.yaml \
     -g java \
     -o generated-sdk
   
   # Generated SDK includes
   ├── ApiClient.java          # HTTP client
   ├── CustomerApi.java        # Endpoint methods
   ├── models/
   │   ├── Customer.java
   │   ├── Order.java
   │   └── Payment.java
   └── README.md
   ```

4. **AsyncAPI for Events**:
   ```yaml
   asyncapi: 2.5.0
   info:
     title: E-Commerce Events
     version: 1.0.0
   
   channels:
     order.created:
       description: Order created by customer
       subscribe:
         message:
           contentType: application/json
           payload:
             type: object
             properties:
               orderId:
                 type: string
               customerId:
                 type: string
               totalAmount:
                 type: number
   ```

---

## Key Takeaways

### Progression of Complexity
1. **Phases 1-3**: Foundation (Java standards, testing, security)
2. **Phases 4-5**: Optimization (caching, monitoring)
3. **Phases 6-9**: Enterprise features (events, gateway, monitoring, K8s)
4. **Phases 10-13**: Production readiness (K8s, DB migrations, CI/CD, docs)

### Critical Concepts
- **Loose Coupling**: Services communicate via events, not direct calls
- **High Cohesion**: Each service owns its domain
- **Observability-First**: Every component instrumented for monitoring
- **Failure Resilience**: Expect failures, design for graceful degradation
- **Automation**: All deployments and tests automated

### Next Learning Path
- Phase 14: Infrastructure as Code (Terraform)
- Phase 15: Advanced Security Patterns
- Phase 16: Disaster Recovery
- Phase 17: Auto-scaling & HA

