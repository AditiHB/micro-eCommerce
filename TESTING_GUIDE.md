# E-Commerce Microservices Testing Guide

## Overview

This document outlines the comprehensive testing strategy for the e-commerce microservices platform following the testing pyramid approach:
- **Unit Tests**: 70% - Repository and Service layer tests
- **Integration Tests**: 25% - Spring context, database, and controller tests
- **E2E Tests**: 5% - Complete order flow validation with TestContainers

## Testing Pyramid Structure

```
        /\
       /  \       5% E2E Tests
      /____\      (Complete workflows)
     /      \
    /  25%   \    Integration Tests
   /  Integration\  (Controllers, Service+DB)
  /____________\
 /              \
/ 70% Unit Tests \ (Repositories, Services)
/__________________\
```

## Phase 1: Unit Tests (Repository & Service)

### What is Tested
- **Repository Tests**: CRUD operations with H2 in-memory database
- **Service Tests**: Business logic with mocked dependencies
- **Coverage Target**: 60-70% per service

### Test Files

#### Customer Service
- `CustomerRepositoryTest`: Save, retrieve, update, delete, pagination
- `CustomerServiceTest`: Business logic, error handling, caching behavior

#### Order Service
- `OrderRepositoryTest`: Order persistence, status management
- `OrderServiceTest`: Order creation, event publishing, pagination

#### Inventory Service
- `InventoryServiceTest`: Stock reservation, release, updates, edge cases

#### Payment Service
- `PaymentServiceTest`: Payment processing, refund handling

### Running Unit Tests

```bash
# Run all unit tests
mvn test

# Run specific service tests
mvn test -pl services/customer-service

# Run specific test class
mvn test -Dtest=CustomerServiceTest

# Run with coverage
mvn clean test jacoco:report
```

### Test Profiles
- `test` profile: Uses H2 in-memory database for fast execution

## Phase 2: Integration Tests

### What is Tested
- **Controller Tests**: HTTP endpoints with MockMvc
- **Service + Database**: Real database interactions with Spring Boot Test
- **Transactional behavior**: @Transactional annotation effects
- **Error handling**: GlobalExceptionHandler validation
- **Coverage Target**: 80%+ of service boundaries

### Test Files
- `*ControllerIntegrationTest`: REST endpoint validation
- `*ServiceIntegrationTest`: Service layer with real database
- `EventListenerIntegrationTest`: Kafka consumer behavior

### Running Integration Tests

```bash
# Run integration tests (includes all tests)
mvn verify

# Run with specific profile
mvn test -Dspring.profiles.active=test

# Run with PostgreSQL (requires running PostgreSQL)
mvn test -Dspring.datasource.url=jdbc:postgresql://localhost:5432/test_db
```

### Test Profiles
- `test` profile: H2 in-memory database
- `postgres`: Real PostgreSQL database (for local development)

## Phase 3: Kafka Event Testing

### What is Tested
- Event publishing from services
- Event consumption in listeners
- Event schema validation
- Dead letter queue handling
- At-least-once delivery semantics

### Test Files
- `OrderEventListenerTest`: Saga pattern validation
- `EventPublisherTest`: Event publishing to Kafka
- `PaymentEventListenerTest`: Payment event consumption

### Running Kafka Tests

```bash
# Run Kafka-specific tests
mvn test -Dtest=*EventListener*

# Run embedded Kafka tests (no external Kafka needed)
mvn test -Dtest=EventPublisherTest
```

### Kafka Test Setup
- Uses Spring Kafka Test with embedded broker
- No external Kafka installation needed
- Automatically started/stopped with tests

## Phase 4: End-to-End (E2E) Tests

### What is Tested
- Complete order flow: Customer → Order → Inventory → Payment
- Saga pattern with compensating transactions
- Failure scenarios and recovery
- Service integration points

### Test Files
- `OrderSagaE2ETest`: Complete happy path and failure paths
- `OrderFlowE2ETest`: Multi-service order processing

### Running E2E Tests

```bash
# Run E2E tests only
mvn verify -Dit.test=*E2ETest

# Run with TestContainers
mvn verify -DskipITs=false

# Run specific E2E test
mvn test -Dtest=OrderSagaE2ETest
```

### TestContainers Setup
- Automatically spins up PostgreSQL
- Automatically spins up Kafka
- Automatically tears down after tests
- No manual infrastructure setup needed

## Test Execution Strategies

### Fast Feedback Loop (Development)
```bash
mvn test -pl services/customer-service
```

### Full Test Suite (Before PR)
```bash
mvn clean verify
```

### Specific Feature Testing
```bash
mvn test -Dtest=CustomerServiceTest,CustomerRepositoryTest
```

### Test Coverage Report
```bash
mvn clean test jacoco:report
# Report: target/site/jacoco/index.html
```

## Coverage Targets

| Component | Unit | Integration | E2E | Total |
|-----------|------|-------------|-----|-------|
| Customer Service | 65% | 20% | 5% | 70% |
| Order Service | 65% | 20% | 5% | 70% |
| Inventory Service | 65% | 20% | 5% | 70% |
| Payment Service | 65% | 20% | 5% | 70% |
| **Overall** | **70%** | **25%** | **5%** | **80%+** |

## Test Naming Conventions

### Unit Tests
```
[Class]Test.java
- test[Method][Scenario][Expected]
- Example: testCreateCustomerSuccess
- Example: testGetCustomerNotFound
```

### Integration Tests
```
[Class]IntegrationTest.java
- test[Feature][Scenario]
- Example: testCreateCustomerWithValidEmail
```

### E2E Tests
```
[Feature]E2ETest.java
- test[Scenario]
- Example: testCompleteOrderFlow
```

## Dependencies for Testing

### Core Testing Framework
- **JUnit 5**: Test execution
- **Mockito**: Mocking and verification
- **AssertJ**: Fluent assertions

### Spring Testing
- **Spring Boot Test**: Integration testing support
- **Spring Test**: MockMvc, @DataJpaTest

### Container & Infrastructure
- **TestContainers**: Docker-based infrastructure
  - PostgreSQL container
  - Kafka container
- **Spring Kafka Test**: Embedded Kafka broker

### REST Testing
- **RestAssured**: REST API validation

## Debugging Tests

### Enable Test Logging
```properties
# application-test.properties
logging.level.com.ecommerce=DEBUG
logging.level.org.springframework=INFO
```

### Run Single Test with Debugging
```bash
mvn test -Dtest=CustomerServiceTest#testCreateCustomer -X
```

### View Test Output
```bash
mvn test -Dtest=CustomerServiceTest -DtestFailureIgnore=true
```

## Best Practices

1. **Isolation**: Each test should be independent
2. **Clarity**: Test names describe what they test
3. **Arrange-Act-Assert**: Follow AAA pattern
4. **No Manual Setup**: Use @BeforeEach for setup
5. **Clean Database**: @BeforeEach clears data
6. **Meaningful Assertions**: Use AssertJ for readability
7. **Mock External Dependencies**: Only mock non-core layers
8. **Test Business Logic**: Don't just verify getters/setters

## CI/CD Integration

### GitHub Actions
Tests run automatically on:
- Push to main/develop branches
- Pull requests

### Test Stages
1. **Unit Tests** (2-3 min): Fast feedback
2. **Integration Tests** (5-7 min): Database interactions
3. **E2E Tests** (10-15 min): Complete flows
4. **Coverage Report**: Verify coverage targets

## Troubleshooting

### Flaky Tests
- Use `@Transactional` to ensure database state
- Use `@BeforeEach` to reset state
- Avoid timing dependencies
- Use Thread.sleep() sparingly

### Port Conflicts
- Tests use random ports by default
- Configure specific ports if needed in application-test.properties

### Docker Issues (TestContainers)
- Ensure Docker daemon is running
- Check Docker permissions for your user
- Use `docker ps` to verify container startup

### Test Timeout
- Increase timeout in pom.xml if needed
- Decrease transaction rollback time
- Use `-DargLine="-Xms512m -Xmx1024m"` for memory issues

## References

- [Spring Boot Testing Documentation](https://spring.io/guides/gs/testing-web/)
- [JUnit 5 Documentation](https://junit.org/junit5/docs/current/user-guide/)
- [Mockito Documentation](https://javadoc.io/doc/org.mockito/mockito-core/latest/org/mockito/Mockito.html)
- [TestContainers Documentation](https://www.testcontainers.org/)
- [AssertJ Documentation](https://assertj.github.io/assertj-core/api/org/assertj/core/api/Assertions.html)
