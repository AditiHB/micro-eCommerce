# micro-eCommerce

A **E-Commerce Microservices Architecture** (security-hardened reference implementation; see [docs/SECURITY_HARDENING.md](docs/SECURITY_HARDENING.md) for what is and is not production-grade) built with **Java 17**, **Spring Boot 3.2.5**, and **Spring Cloud 2023.0.1**. This project demonstrates a scalable, resilient, and event-driven architecture for modern e-commerce platforms.

## 📋 Table of Contents

- [Overview](#overview)
- [Architecture](#architecture)
- [Microservices](#microservices)
- [Technology Stack](#technology-stack)
- [Project Structure](#project-structure)
- [Getting Started](#getting-started)
- [Docker Deployment](#docker-deployment)
- [API Documentation](#api-documentation)
- [Key Features](#key-features)
- [Documentation](#-documentation)

---

## 🎯 Overview

This project implements a cloud-native microservices architecture for an e-commerce platform, featuring:

- **Independent microservices** for different business domains (Orders, Inventory, Payments, Customers)
- **Centralized configuration management** via Spring Cloud Config
- **Dynamic service discovery** using Netflix Eureka
- **Single entry point** with Spring Cloud Gateway API Gateway
- **Event-driven architecture** using Apache Kafka for asynchronous communication
- **SAGA pattern** for distributed transactions across services
- **Resilience patterns** (Circuit Breaker, Retry) using Resilience4j
- **Docker containerization** with Docker Compose orchestration
- **Identity and access control** with Keycloak (OAuth2/OIDC, RS256 tokens, role + object-level authorization) - see [docs/KEYCLOAK_IDENTITY.md](docs/KEYCLOAK_IDENTITY.md)
- **Secrets** from git-ignored generated files, or HashiCorp Vault (AppRole, dynamic short-lived database logins) - see [docs/VAULT_SECRETS.md](docs/VAULT_SECRETS.md)
- **Internal PKI** with smallstep step-ca (24h auto-renewed certificates, edge TLS) - see [docs/SMALLSTEP_PKI.md](docs/SMALLSTEP_PKI.md)

---

## 🏗️ Architecture

### Core Architecture Patterns

#### 1. **Service Discovery Pattern**
- **Technology:** Netflix Eureka
- **Purpose:** Automatic service registration and discovery, eliminating hardcoded service endpoints
- **Runs on:** Port 8761

#### 2. **Config Server Pattern**
- **Technology:** Spring Cloud Config Server
- **Purpose:** Centralized, externalized configuration management backed by Git repository
- **Runs on:** Port 8888
- **Git Repository:** `./config-repo`

#### 3. **API Gateway Pattern**
- **Technology:** Spring Cloud Gateway
- **Purpose:** Single entry point for all client requests, handles routing, security, CORS, and rate limiting
- **Runs on:** Port 8080

#### 4. **Event-Driven Communication**
- **Technology:** Apache Kafka with Zookeeper
- **Purpose:** Asynchronous, non-blocking inter-service communication using domain events
- **Kafka Port:** 9092
- **Zookeeper Port:** 2181

#### 5. **Database per Service Pattern**
- Each microservice maintains its own independent database
- Ensures loose coupling and independent scaling
- Supports polyglot persistence

#### 6. **SAGA Pattern (Choreography)**
- Manages distributed transactions without two-phase commit (2PC)
- Services communicate via events to coordinate multi-step business processes
- Includes compensation logic for rollback scenarios

---

## 🚀 Microservices

### 1. **Customer Service** (Port 8081)
- **Responsibility:** Customer profile management, authentication, account details
- **Key Features:**
  - Customer registration and profile management
  - Shipping and billing address management
  - Account verification and authentication

### 2. **Inventory Service** (Port 8082)
- **Responsibility:** Product stock management and reservation
- **Key Features:**
  - Stock level tracking
  - Inventory reservation during order placement
  - Stock replenishment management
  - Event-driven stock updates

### 3. **Order Service** (Port 8083)
- **Responsibility:** Order lifecycle management
- **Key Features:**
  - Order creation and validation
  - Order status tracking (PENDING, COMPLETED, CANCELLED)
  - Saga choreography for order fulfillment
  - Order history and retrieval

### 4. **Payment Service** (Port 8084)
- **Responsibility:** Payment processing and transaction management
- **Key Features:**
  - Payment processing and validation
  - Multiple payment gateway support
  - Refund management
  - Transaction logging and reconciliation

### 5. **Product Service** (Port 8085)
- **Responsibility:** Product catalog management and inventory coordination
- **Key Features:**
  - Product CRUD operations (Create, Read, Update, Delete)
  - Product catalog browsing with pagination and filtering
  - Product categorization
  - Inventory tracking and availability monitoring
  - Event-driven updates with Kafka
  - Multi-database support (H2, PostgreSQL, Oracle)
  - Product search and low-stock alerts

---

## 💻 Technology Stack

### Backend
- **Java:** 17 (LTS)
- **Spring Boot:** 3.2.5
- **Spring Cloud:** 2023.0.1
- **Build Tool:** Maven 3

### Infrastructure
- **API Gateway:** Spring Cloud Gateway
- **Service Discovery:** Netflix Eureka
- **Configuration Server:** Spring Cloud Config
- **Message Broker:** Apache Kafka 7.3.2
- **Coordination:** Zookeeper 7.3.2
- **Load Balancer:** Spring Cloud LoadBalancer

### Resilience & Monitoring
- **Resilience4j:** Circuit Breaker, Retry, Rate Limiting
- **Logging:** SLF4J + Logback

### Containerization & Orchestration
- **Docker:** Container runtime
- **Docker Compose:** Local orchestration

---

## 📁 Project Structure

```
micro-eCommerce/
├── README.md                                    # Project documentation
├── DOCUMENTATION_INDEX.md                       # Guided index into docs/
├── pom.xml                                      # Parent Maven POM
├── docker-compose.yml                           # Single Compose file for ALL local scenarios (see docs/SETUP_AND_DEPLOYMENT.md)
├── .env.postgres                                # Env flags to switch the stack to Postgres
├── .env                                         # (generated, git-ignored) your secrets - scripts/gen-env.sh
├── docker-compose.vault.yml / .pki.yml / .vault-tls.yml   # optional overlays: Vault, internal CA, Vault over TLS
├── postman-collection.json                     # API test collection
├── postman_environment_http.json               # Postman/Insomnia env (HTTP)
├── postman_environment_https.json              # Postman/Insomnia env (HTTPS)
├── docs/                                        # All project documentation (architecture, setup, security, concepts)
│
├── common/                                      # Shared utilities and models
│   └── src/
│
├── infrastructure/                              # Infrastructure services
│   ├── api-gateway/                            # Spring Cloud Gateway
│   │   └── src/
│   ├── config-server/                          # Spring Cloud Config Server
│   │   └── src/
│   └── discovery-server/                       # Netflix Eureka Server
│       └── src/
│
├── services/                                    # Core microservices
│   ├── customer-service/                       # Customer management
│   │   ├── pom.xml
│   │   └── src/
│   ├── inventory-service/                      # Inventory management
│   │   ├── pom.xml
│   │   └── src/
│   ├── order-service/                          # Order management
│   │   ├── pom.xml
│   │   └── src/
│   ├── payment-service/                        # Payment processing
│   │   ├── pom.xml
│   │   └── src/
│   ├── notification-service/                   # Customer/order notifications
│   │   ├── pom.xml
│   │   └── src/
│   └── product-service/                        # Product catalog management
│       ├── pom.xml
│       └── src/
│
├── e2e-tests/                                    # Karate end-to-end tests (see e2e-tests/README.md)
├── k8s/                                         # Kubernetes manifests (see docs/KUBERNETES_DEPLOYMENT.md)
└── config-repo/                                 # Centralized configuration files
```

---

## 🚀 Getting Started

### Prerequisites

- **Java 17+** - Install from [oracle.com](https://www.oracle.com/java/technologies/downloads/)
- **Maven 3.8+** - Install from [maven.apache.org](https://maven.apache.org/)
- **Docker & Docker Compose** - Install from [docker.com](https://www.docker.com/)
- **Git** - For version control

### Local Development Setup

#### 1. Clone the Repository
```bash
git clone <repository-url>
cd micro-eCommerce
```

#### 2. Build All Modules
```bash
mvn clean install
```

#### 3. Run Services Individually (Development)

**Terminal 1: Config Server**
```bash
cd infrastructure/config-server
mvn spring-boot:run
```

**Terminal 2: Discovery Server (Eureka)**
```bash
cd infrastructure/discovery-server
mvn spring-boot:run
```

**Terminal 3: API Gateway**
```bash
cd infrastructure/api-gateway
mvn spring-boot:run
```

**Terminal 4: Customer Service**
```bash
cd services/customer-service
mvn spring-boot:run
```

**Terminal 5: Inventory Service**
```bash
cd services/inventory-service
mvn spring-boot:run
```

**Terminal 6: Order Service**
```bash
cd services/order-service
mvn spring-boot:run
```

**Terminal 7: Payment Service**
```bash
cd services/payment-service
mvn spring-boot:run
```

**Terminal 8: Product Service**
```bash
cd services/product-service
mvn spring-boot:run
```

---

## 🐳 Docker Deployment

### Quick Start with Docker Compose

No credential is stored in this repository. Generate your own secrets first (random, git-ignored `.env`):

```bash
scripts/gen-env.sh          # creates .env: database/Redis passwords, Keycloak admin, client secrets, test-user passwords
```

A single `docker-compose.yml` covers the local scenarios via Compose **profiles**, and two optional **overlay files** add Vault and the internal CA. This is a local-only project (Docker Compose here, or the Kubernetes manifests under `k8s/` for a cluster); there is no distinct "production" compose variant. Every published port is bound to `127.0.0.1`.

```bash
# Default: core stack, in-memory H2 (fastest to start). Keycloak is part of the core stack.
docker compose up -d --build

# Real PostgreSQL instead of H2 (one least-privilege owner role per service)
docker compose --env-file .env --env-file .env.postgres --profile postgres up -d --build

# Secrets from HashiCorp Vault (+ short-lived DB logins with Postgres)
docker compose -f docker-compose.yml -f docker-compose.vault.yml up -d --build

# HTTPS: internal smallstep CA issues the certificates (nginx on :443, Keycloak on :8443)
docker compose -f docker-compose.yml -f docker-compose.pki.yml --profile https up -d --build

# Observability (ELK/Prometheus/Grafana) on top of any of the above
#   ... --profile observability

# Check service status / logs / stop
docker compose ps
docker compose logs -f
docker compose down            # add -v to also drop volumes (database, Keycloak, Vault, CA)
```

**Getting a token** (there is no login endpoint on the API - Keycloak issues every token):

```bash
set -a; . ./.env; set +a
TOKEN=$(curl -s http://localhost:8180/realms/ecommerce/protocol/openid-connect/token   -d grant_type=password -d client_id=ecommerce-e2e -d "client_secret=$E2E_CLIENT_SECRET"   -d username=karate_admin -d "password=$E2E_ADMIN_PASSWORD" | python3 -c 'import sys,json;print(json.load(sys.stdin)["access_token"])')
curl -H "Authorization: Bearer $TOKEN" http://localhost:8080/api/customers
```

See **[docs/SETUP_AND_DEPLOYMENT.md](docs/SETUP_AND_DEPLOYMENT.md)** for the full scenario matrix, memory budget per profile, and HTTPS certificate setup.

### Service Access via Docker Compose

- **API Gateway:** http://localhost:8080 (the only public API entry point)
- **Keycloak:** http://localhost:8180 (admin console; `KC_BOOTSTRAP_ADMIN_*` in `.env`)
- **Eureka Dashboard:** http://localhost:8761 (HTTP Basic: user `eureka`, password `EUREKA_PASSWORD` in `.env`)
- **Config Server:** http://localhost:8888 (HTTP Basic)
- **Customer Service:** http://localhost:8081
- **Inventory Service:** http://localhost:8082
- **Order Service:** http://localhost:8083
- **Payment Service:** http://localhost:8084
- **Product Service:** http://localhost:8085
- **Kafka Broker:** localhost:9092
- **Zookeeper:** localhost:2181

### Building Docker Images

Each microservice can be built independently:

```bash
cd services/customer-service
docker build -t customer-service:1.0 .
```

---

## 📚 API Documentation

### Postman Collection

A complete Postman collection is provided: `postman-collection.json` (also importable into Insomnia). Two environments are included - `postman_environment_http.json` for the default HTTP setup and `postman_environment_https.json` for the `--profile https` nginx setup.

**Import steps:**
1. Open Postman (or Insomnia)
2. Click **Import** → select `postman-collection.json` and the environment file matching your setup
3. In the environment set `keycloak_client_secret`, `username` and `password` from your `.env`, then run *Authentication → Get access token* (see [docs/KEYCLOAK_IDENTITY.md](docs/KEYCLOAK_IDENTITY.md#4-getting-a-token))
4. Explore available endpoints for all microservices

### Base URL
```
http://localhost:8080/api
```

### Sample Endpoints

#### Customer Service
- `POST /api/customers` - Create a new customer
- `GET /api/customers/{id}` - Get customer details
- `PUT /api/customers/{id}` - Update customer
- `DELETE /api/customers/{id}` - Delete customer

#### Inventory Service
- `GET /api/inventory` - List all products
- `GET /api/inventory/{id}` - Get product details
- `POST /api/inventory/{id}/reserve` - Reserve stock
- `POST /api/inventory/{id}/release` - Release reservation

#### Order Service
- `POST /api/orders` - Create a new order
- `GET /api/orders/{id}` - Get order details
- `GET /api/orders` - List all orders
- `PUT /api/orders/{id}/cancel` - Cancel order

#### Payment Service
- `POST /api/payments` - Process payment
- `GET /api/payments/{id}` - Get payment details
- `POST /api/payments/{id}/refund` - Process refund

---

## ✨ Key Features

### 1. **Scalability**
- Independent service scaling
- Load balancing across instances
- Asynchronous communication reduces coupling

### 2. **Resilience**
- Circuit Breaker pattern prevents cascading failures
- Automatic retry mechanisms for transient failures
- Graceful degradation with fallback responses

### 3. **Observability**
- Centralized logging
- Distributed tracing ready
- Eureka dashboard for service health

### 4. **Data Consistency**
- SAGA pattern for distributed transactions
- Event sourcing capabilities
- Compensation logic for failure scenarios

### 5. **Security**
- API Gateway authentication/authorization
- Service-to-service communication
- Configuration security via Spring Cloud Config

### 6. **Developer Experience**
- Centralized configuration management
- Automatic service discovery
- Hot reload capabilities
- Docker Compose for easy local development

---

## 📖 Development Phases

The project follows a structured, 13-phase development approach (infrastructure, security, resilience, observability, Kubernetes, CI/CD, and more).

See **[docs/PHASES_GUIDE.md](docs/PHASES_GUIDE.md)** for the phase-by-phase breakdown and **[docs/ARCHITECTURE.md](docs/ARCHITECTURE.md)** for the overall system design.

---

## 🔧 Configuration Management

### Config Server Structure

Configuration files are stored in `./config-repo` and organized by service:

```
config-repo/
├── application.yml                    # Global configuration
├── customer-service.yml              # Customer service config
├── inventory-service.yml             # Inventory service config
├── order-service.yml                 # Order service config
└── payment-service.yml               # Payment service config
```

### Environment Variables

Services can be configured via environment variables:

```bash
SPRING_CONFIG_IMPORT=optional:configserver:http://config-server:8888/
EUREKA_CLIENT_SERVICEURL_DEFAULTZONE=http://discovery-server:8761/eureka/
SPRING_KAFKA_BOOTSTRAP_SERVERS=kafka:29092
```

---

## 🧪 Testing

### Unit Tests
```bash
mvn test
```

### Integration Tests
```bash
mvn verify
```

### Building with Tests
```bash
mvn clean install -DskipTests  # Skip tests
mvn clean install              # Run all tests
```

---

## 📊 Monitoring & Health Checks

### Service Health Endpoints

Each service exposes health check endpoints:

```bash
# Customer Service Health
curl http://localhost:8081/actuator/health

# Inventory Service Health
curl http://localhost:8082/actuator/health

# Order Service Health
curl http://localhost:8083/actuator/health

# Payment Service Health
curl http://localhost:8084/actuator/health
```

### Eureka Dashboard

Access the Eureka dashboard to monitor service registration:
```
http://localhost:8761
```

---

## 🤝 Contributing

Contributions are welcome! Please follow these guidelines:

1. Create a feature branch (`git checkout -b feature/AmazingFeature`)
2. Commit your changes (`git commit -m 'Add some AmazingFeature'`)
3. Push to the branch (`git push origin feature/AmazingFeature`)
4. Open a Pull Request

---

## 📝 License

This project is licensed under the MIT License - see the LICENSE file for details.

---

## 📧 Contact & Support

For questions or support, please open an issue in the repository.

---

## 📚 Documentation

All project documentation lives under [`docs/`](docs/). Start with **[DOCUMENTATION_INDEX.md](DOCUMENTATION_INDEX.md)** for a guided learning path, or jump straight to:

- **[docs/SETUP_AND_DEPLOYMENT.md](docs/SETUP_AND_DEPLOYMENT.md)** - every local setup scenario (H2, Postgres, HTTPS, full/compact stack), Kubernetes, testing, troubleshooting
- **[docs/ARCHITECTURE.md](docs/ARCHITECTURE.md)** - system design overview
- **[docs/PHASES_GUIDE.md](docs/PHASES_GUIDE.md)** - phase-by-phase implementation walkthrough
- **[docs/CONCEPTS_EXPLAINED.md](docs/CONCEPTS_EXPLAINED.md)** - distributed systems concepts used in this project
- **[docs/SAGA_PATTERN_GUIDE.md](docs/SAGA_PATTERN_GUIDE.md)**, **[docs/DATABASE_MIGRATIONS.md](docs/DATABASE_MIGRATIONS.md)**, **[docs/TESTING_GUIDE.md](docs/TESTING_GUIDE.md)**, **[docs/KUBERNETES_DEPLOYMENT.md](docs/KUBERNETES_DEPLOYMENT.md)**
- **[docs/SSL_TLS_SETUP.md](docs/SSL_TLS_SETUP.md)**, **[docs/SECRETS_MANAGEMENT.md](docs/SECRETS_MANAGEMENT.md)**, **[docs/MTLS_CONFIGURATION.md](docs/MTLS_CONFIGURATION.md)**, **[docs/COMPLIANCE_MONITORING.md](docs/COMPLIANCE_MONITORING.md)** - security deep dives
- **[docs/concepts/](docs/concepts/)** - beginner-friendly explainers for Docker, Kafka, Kubernetes, microservices, and transactions

---

## 🎓 Learning Resources

- [Spring Boot Documentation](https://spring.io/projects/spring-boot)
- [Spring Cloud Documentation](https://spring.io/projects/spring-cloud)
- [Microservices Patterns](https://microservices.io/patterns/index.html)
- [Event Sourcing & SAGA Pattern](https://microservices.io/patterns/data/saga.html)
- [Kafka Documentation](https://kafka.apache.org/documentation/)
- [Docker Documentation](https://docs.docker.com/)

---

**Last Updated:** 2026-09-27
