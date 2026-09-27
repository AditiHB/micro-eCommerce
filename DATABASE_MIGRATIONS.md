# Database Migrations Guide

## Overview

This project uses **Liquibase** for database version control and schema management across all microservices. Liquibase enables safe, versioned database changes with rollback capabilities.

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

Each service has a changelog master file:

```
services/
├── customer-service/
│   └── src/main/resources/db/changelog/
│       └── db.changelog-master.xml
├── order-service/
│   └── src/main/resources/db/changelog/
│       └── db.changelog-master.xml
├── payment-service/
│   └── src/main/resources/db/changelog/
│       └── db.changelog-master.xml
└── inventory-service/
    └── src/main/resources/db/changelog/
        └── db.changelog-master.xml
```

## ChangeSets

Each service's `db.changelog-master.xml` contains ordered changesets:

### Customer Service
1. **001-initial-customers-schema**: Creates `customers` table with indexes
2. **002-customers-indexes**: Creates optimized indexes
3. **003-audit-log-table**: Creates `audit_logs` table for change tracking
4. **004-audit-log-indexes**: Indexes for audit log queries

### Order Service
1. **001-initial-orders-schema**: Creates `orders` table
2. **002-orders-indexes**: Composite indexes for query optimization
3. **003-event-sourcing-table**: Creates `order_events` table
4. **004-event-sourcing-indexes**: Event table indexes
5. **005-audit-log-table**: Audit logging
6. **006-audit-log-indexes**: Audit table indexes

### Payment Service
1. **001-initial-payments-schema**: Creates `payments` table
2. **002-payments-indexes**: Query optimization indexes
3. **003-payment-events-table**: Creates `payment_events` table
4. **004-payment-events-indexes**: Event sourcing indexes
5. **005-audit-log-table**: Audit logging
6. **006-audit-log-indexes**: Audit indexes

### Inventory Service
1. **001-initial-inventory-schema**: Creates `inventory` table
2. **002-inventory-indexes**: Query performance indexes
3. **003-inventory-events-table**: Creates `inventory_events` table
4. **004-inventory-events-indexes**: Event sourcing indexes
5. **005-audit-log-table**: Audit logging
6. **006-audit-log-indexes**: Audit indexes

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
  liquibase:
    enabled: true
    change-log: classpath:db/changelog/db.changelog-master.xml
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

Migrations run automatically on application startup via Liquibase Spring Boot integration.

```bash
# Development (H2)
mvn spring-boot:run

# Production (PostgreSQL)
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

### Automatic Rollback
Liquibase stores migration history in `DATABASECHANGELOG` table. To rollback:

```bash
# Rollback to specific date
mvn liquibase:rollback -Dliquibase.rollbackDate=2024-01-01

# Rollback specific number of changesets
mvn liquibase:rollback -Dliquibase.rollbackCount=3

# Rollback to specific tag
mvn liquibase:rollback -Dliquibase.rollbackTag=v1.0
```

### Manual Rollback
If Liquibase rollback is not possible, manually execute reverse SQL:

```sql
-- Example rollback
DROP TABLE orders;
DROP TABLE DATABASECHANGELOG;
DROP TABLE DATABASECHANGELOGLOCK;
```

### Precautions
1. Always backup data before migrations
2. Test migrations on staging environment first
3. Schedule migrations during maintenance windows
4. Have a rollback plan documented
5. Monitor application logs after migration

## Liquibase Monitoring

### Check Migration Status
```bash
mvn liquibase:status
```

### View Change History
```bash
mvn liquibase:history
```

### Generate SQL Preview
```bash
mvn liquibase:updateSQL
```

### Validate Changelog
```bash
mvn liquibase:validate
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

1. **One Change Per Changeset**: Each logical database change should be a separate changeset
2. **Version Control**: Keep all migration files in version control
3. **Test First**: Always test migrations on staging before production
4. **Document Changes**: Add comments explaining the "why" behind schema changes
5. **Use Contexts**: Tag changesets with contexts (dev, test, prod) for environment-specific changes
6. **Validate Schema**: Run `liquibase:validate` before deploying
7. **Monitor Performance**: Track migration execution time and database performance

## Troubleshooting

### Migration Failed
1. Check logs: `mvn liquibase:status`
2. Verify SQL syntax in changelog
3. Ensure database connectivity
4. Check user permissions

### Lock Issues
If Liquibase locks up:
```sql
-- Reset lock (use with caution)
DELETE FROM DATABASECHANGELOGLOCK;
```

### Rollback Not Working
1. Verify `rollbackSQL` is defined in changeset
2. Check `supportsRollback="true"` attribute
3. Manual rollback may be required for non-rollbackable operations

## Future Enhancements

- [ ] Implement data migration scripts for schema changes
- [ ] Add pre/post-migration validation checks
- [ ] Implement partition strategy for Orders and Events tables
- [ ] Add materialized views for reporting
- [ ] Implement sharding strategy documentation
- [ ] Add database performance baselines

## References

- [Liquibase Documentation](https://docs.liquibase.com/)
- [Spring Boot Liquibase Integration](https://spring.io/guides/gs/database-migrations-with-flyway/)
- [PostgreSQL Best Practices](https://wiki.postgresql.org/wiki/Performance_Optimization)
