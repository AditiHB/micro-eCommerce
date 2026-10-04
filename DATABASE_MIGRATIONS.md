# Database Migrations Guide

## Overview

This project uses **Flyway** for database version control and schema management across all microservices. See [db/README.md](db/README.md) for the authoritative, up-to-date guide to how migrations are structured and run - this document covers schema design conventions and operational practices.

## Architecture

### Services & Databases

- **customer-service**: `customer_db`
  - Tables: `customers`, `audit_logs`
  
- **order-service**: `order_db`
  - Tables: `orders`, `order_events`, `audit_logs`
  
- **payment-service**: `payment_db`
  - Tables: `payments`, `payment_events`, `audit_logs`
  
- **inventory-service**: `inventory_db`
  - Tables: `inventory`, `inventory_events`, `audit_logs`

## Migration Files Structure

Each service has its own versioned Flyway migrations, one subfolder per database vendor:

```
services/
├── customer-service/
│   └── src/main/resources/db/migration/{h2,oracle,postgresql}/
│       ├── V1__Create_Customers_Table.sql
│       └── ...
├── order-service/
│   └── src/main/resources/db/migration/{h2,oracle,postgresql}/
├── payment-service/
│   └── src/main/resources/db/migration/{h2,oracle,postgresql}/
└── inventory-service/
    └── src/main/resources/db/migration/{h2,oracle,postgresql}/
```

See each service's migration folder for its exact changeset history - that's the source of truth, not this document.

## Database Configuration

### Development (H2 In-Memory)

Default configuration in `application.yml`:
```yaml
spring:
  datasource:
    url: jdbc:h2:mem:customer_db
    driverClassName: org.h2.Driver
    username: sa
    password: password
  jpa:
    hibernate:
      ddl-auto: validate
  flyway:
    enabled: true
    locations: classpath:db/migration/{vendor}
```

H2 console available at: `http://localhost:8081/h2-console`

### Production (PostgreSQL)

Use the `postgres` profile:

```bash
java -Dspring.profiles.active=postgres -jar customer-service.jar
```

Configuration in `application-postgres.yml`:
- **URL**: `jdbc:postgresql://localhost:5432/customer_db`
- **User**: `ecommerce_user` (configure as needed)
- **Password**: `ecommerce_password` (use environment variable in production)
- **Connection Pool**: HikariCP with 20 max connections
- **Batch Size**: 20 for performance optimization

### Running Migrations

Migrations run automatically on application startup via Flyway's Spring Boot integration - there is no separate migrate step.

```bash
# Development (H2)
mvn spring-boot:run

# Against PostgreSQL (see docker-compose-postgres.yml for the containerized equivalent)
mvn spring-boot:run -Dspring-boot.run.arguments="--spring.profiles.active=postgres"
```

## Database Setup (PostgreSQL)

For production databases, create the following schema:

```sql
-- Create databases
CREATE DATABASE customer_db;
CREATE DATABASE order_db;
CREATE DATABASE payment_db;
CREATE DATABASE inventory_db;

-- Create application user
CREATE USER ecommerce_user WITH PASSWORD 'ecommerce_password';

-- Grant privileges
GRANT ALL PRIVILEGES ON DATABASE customer_db TO ecommerce_user;
GRANT ALL PRIVILEGES ON DATABASE order_db TO ecommerce_user;
GRANT ALL PRIVILEGES ON DATABASE payment_db TO ecommerce_user;
GRANT ALL PRIVILEGES ON DATABASE inventory_db TO ecommerce_user;
```

## Schema Details

### Core Tables

#### customers
```sql
CREATE TABLE customers (
    id BIGINT PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    email VARCHAR(255) NOT NULL UNIQUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
```

#### orders
```sql
CREATE TABLE orders (
    id BIGINT PRIMARY KEY,
    customer_id BIGINT NOT NULL,
    product_id VARCHAR(100) NOT NULL,
    quantity INT NOT NULL,
    status VARCHAR(50) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
```

#### payments
```sql
CREATE TABLE payments (
    id BIGINT PRIMARY KEY,
    order_id BIGINT NOT NULL,
    amount DECIMAL(10,2) NOT NULL,
    status VARCHAR(50) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
```

#### inventory
```sql
CREATE TABLE inventory (
    id BIGINT PRIMARY KEY,
    product_id VARCHAR(100) NOT NULL UNIQUE,
    quantity INT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
```

### Event Sourcing Tables

- `order_events`: Captures all order state changes
- `payment_events`: Captures all payment state changes
- `inventory_events`: Captures all inventory state changes

Structure:
```sql
CREATE TABLE {entity}_events (
    id BIGINT PRIMARY KEY,
    {entity}_id BIGINT NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    event_data TEXT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
```

### Audit Tables

Each service has an `audit_logs` table for compliance and debugging:
```sql
CREATE TABLE audit_logs (
    id BIGINT PRIMARY KEY,
    entity_type VARCHAR(100) NOT NULL,
    entity_id BIGINT NOT NULL,
    action VARCHAR(50) NOT NULL,
    changed_fields TEXT,
    old_values TEXT,
    new_values TEXT,
    performed_by VARCHAR(255) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
```

## Index Strategy

### Customer Service
- `uk_customers_email`: Unique index on email (enforces uniqueness)
- `idx_customers_email`: Supports email-based queries
- `idx_customers_created_at`: Supports time-range queries

### Order Service
- `idx_orders_customer_id`: Foreign key optimization
- `idx_orders_product_id`: Product lookup
- `idx_orders_status`: Status filtering (most used queries)
- `idx_orders_created_at`: Time-range queries
- `idx_orders_customer_status`: Composite index for "customer's pending orders"

### Payment Service
- `idx_payments_order_id`: Foreign key optimization
- `idx_payments_status`: Status filtering
- `idx_payments_created_at`: Time-range queries
- `idx_payments_order_status`: Composite for "order payment status"

### Inventory Service
- `uk_inventory_product_id`: Unique constraint
- `idx_inventory_product_id`: Product lookup (unique index)
- `idx_inventory_quantity`: Low stock queries
- `idx_inventory_created_at`: Time-range queries

## Rollback Strategy

Flyway Community Edition (what this project uses) has **no automated rollback** - that's an Enterprise-only feature. The standard Flyway pattern is **roll forward**: write a new migration that undoes or corrects the previous change, rather than reversing history.

```sql
-- Example: V5__Revert_Column_Add.sql, undoing a column added in V4
ALTER TABLE orders DROP COLUMN discount_code;
```

If you genuinely need to discard a migration that was already applied (e.g. in a throwaway dev/CI database), manually repair the history table and drop the affected objects:

```sql
-- Caution: this just forgets the migration happened, it doesn't undo its effects
DELETE FROM flyway_schema_history WHERE version = '5';
DROP TABLE orders; -- only if you're also recreating it from scratch
```

### Precautions
1. Always back up data before migrating a real database
2. Test migrations against staging before production
3. Schedule migrations during maintenance windows for production
4. Prefer additive, backward-compatible changes so a bad deploy doesn't require a schema rollback
5. Monitor application logs after migration

## Flyway Status

### Check pending/applied migrations
```bash
mvn -pl services/<service-name> flyway:info
```

### Validate applied migrations against the files on disk
```bash
mvn -pl services/<service-name> flyway:validate
```

## Performance Optimization

### Batch Operations
PostgreSQL configuration enables batch processing:
- `batch_size: 20` for INSERT/UPDATE operations
- `order_inserts: true` to optimize insert ordering
- `order_updates: true` to optimize update ordering

### Connection Pooling
HikariCP settings for production:
- `maximum-pool-size: 20`
- `minimum-idle: 5`
- Connection timeout: 30 seconds
- Max lifetime: 30 minutes

### Query Optimization
All tables include strategic indexes on:
- Foreign key columns
- Status/state columns
- Timestamp columns for range queries
- Composite indexes for common JOIN patterns

## Best Practices

1. **One Change Per Migration**: Each logical database change should be its own `V{n}__...sql` file
2. **Version Control**: Keep all migration files in version control
3. **Never Edit Applied Migrations**: Flyway checksums each file - editing one that already ran elsewhere breaks validation. Add a new migration instead.
4. **Test First**: Always test migrations against staging before production
5. **Document Changes**: Add SQL comments explaining the "why" behind schema changes
6. **Validate Schema**: Run `flyway:validate` before deploying
7. **Monitor Performance**: Track migration execution time and database performance

## Troubleshooting

### Migration Failed
1. Check logs: `mvn -pl services/<service-name> flyway:info`
2. Verify SQL syntax in the migration file
3. Ensure database connectivity
4. Check user permissions

### Stuck Lock
Flyway takes an advisory lock while migrating. If a crashed process left it held:
```sql
-- Only if you're certain no migration is actually in progress
DELETE FROM flyway_schema_history WHERE success = false;
```

### Checksum Mismatch
A migration file was edited after it was already applied somewhere. Revert the edit and add a new migration instead - or, if you're certain the environment can be reset, run `flyway:repair` to re-baseline the checksums.

## Future Enhancements

- [ ] Implement data migration scripts for schema changes
- [ ] Add pre/post-migration validation checks
- [ ] Implement partition strategy for Orders and Events tables
- [ ] Add materialized views for reporting
- [ ] Implement sharding strategy documentation
- [ ] Add database performance baselines

## References

- [Flyway Documentation](https://flywaydb.org/documentation/)
- [Spring Boot Flyway Integration](https://spring.io/guides/gs/database-migrations-with-flyway/)
- [PostgreSQL Best Practices](https://wiki.postgresql.org/wiki/Performance_Optimization)
