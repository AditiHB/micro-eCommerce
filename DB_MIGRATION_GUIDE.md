# Database Migration Guide

## Overview

This project uses **Flyway** for database migrations. Each microservice owns its own database and its own versioned migration files - there is no shared/monolithic schema and no separate migration tool to install. See [db/README.md](db/README.md) for the authoritative, up-to-date guide to how migrations are laid out and run; this document covers the saga-pattern schema design and operational practices.

## Quick Start

Migrations run automatically when a service starts - you don't install or invoke anything separately. See [db/README.md](db/README.md) for exactly how, including how to point a service at a containerized PostgreSQL instead of the default in-memory H2.

To inspect migrations for a specific service without starting it:
```bash
mvn -pl services/<service-name> flyway:info
mvn -pl services/<service-name> flyway:validate
```

## Migration Files

Each service keeps its own migration history under `services/<service-name>/src/main/resources/db/migration/{h2,oracle,postgresql}/`, following Flyway's standard naming (`V1__Create_X_Table.sql`, `V2__...sql`, etc. - see [db/README.md](db/README.md) for the full per-service list). The schema below describes the conceptual design those migrations implement.

## Saga Pattern Integration

The migration schema fully supports the saga pattern with compensating transactions:

### Forward Transaction Flow
```
1. Order Service: Creates order (status = PENDING)
2. Inventory Service: Reserves stock (status = INVENTORY_RESERVED)
3. Payment Service: Processes payment (status = PAYMENT_PROCESSING → PROCESSED)
4. Order Service: Completes order (status = COMPLETED)
```

### Compensation Flow (if failure occurs)
```
1. Payment fails → status = FAILED
2. Inventory Service: Releases reserved stock (compensating transaction)
3. Payment Service: Refunds payment if needed (status = REFUNDED)
4. Order Service: Cancels order (status = CANCELLED)
```

## Database Configuration

### Environment Variables

Set these in your deployment environment:

```bash
# MySQL
export DB_HOST=localhost
export DB_PORT=3306
export DB_NAME=micro_ecommerce
export DB_USERNAME=ecommerce_user
export DB_PASSWORD=secure_password
export DB_DRIVER=com.mysql.cj.jdbc.Driver

# Or PostgreSQL
export DB_DRIVER=org.postgresql.Driver
```

### Spring Boot Configuration

In `application-production.yml`:
```yaml
spring:
  datasource:
    url: jdbc:mysql://${DB_HOST}:${DB_PORT}/${DB_NAME}?serverTimezone=UTC&useUnicode=true&characterEncoding=utf8
    username: ${DB_USERNAME}
    password: ${DB_PASSWORD}
    driver-class-name: ${DB_DRIVER}
    hikari:
      maximum-pool-size: 20
      minimum-idle: 5
      connection-timeout: 30000
      idle-timeout: 600000
  jpa:
    hibernate:
      ddl-auto: validate  # Don't auto-create with migrations in place
    properties:
      hibernate:
        dialect: org.hibernate.dialect.MySQL8Dialect
        format_sql: true
        jdbc:
          batch_size: 20
          fetch_size: 50
```

## Best Practices

### 1. Always Backup Before Migration
```bash
mysqldump -u root -p micro_ecommerce > backup_$(date +%Y%m%d_%H%M%S).sql
```

### 2. Test Migrations in Staging First
- Run migrations on a staging database
- Verify application works with new schema
- Run through complete user workflows
- Check performance with realistic data volume

### 3. Naming Convention
- `V{version}__{description}.sql` (e.g., `V1__Create_Initial_Schema.sql`)
- Use double underscore to separate version and description
- Flyway Community Edition (what this project uses) has no undo/rollback migrations - that's a Teams/Enterprise feature. To reverse a change, write a new forward migration instead.

### 4. Never Modify Applied Migrations
- Once a migration is applied, never edit it
- Always create a new migration for changes
- This maintains schema history consistency

### 5. Idempotent Migrations
- Use `CREATE TABLE IF NOT EXISTS`
- Use `IF NOT EXISTS` for indexes
- Use `ON DUPLICATE KEY UPDATE` for inserts
- Safe to run multiple times

### 6. Performance Considerations
- Add indexes strategically
- Use appropriate data types
- Consider charset/collation for international support
- Monitor slow query logs after migration

## Verification

After migrations complete, verify schema:

```sql
-- Check tables exist
SHOW TABLES;

-- Verify table structure
DESCRIBE customers;
DESCRIBE orders;
DESCRIBE inventory;
DESCRIBE payments;

-- Check row counts
SELECT 'customers' as table_name, COUNT(*) FROM customers
UNION ALL
SELECT 'orders', COUNT(*) FROM orders
UNION ALL
SELECT 'inventory', COUNT(*) FROM inventory
UNION ALL
SELECT 'payments', COUNT(*) FROM payments;

-- Verify indexes
SHOW INDEXES FROM orders;

-- Check foreign key constraints
SELECT CONSTRAINT_NAME, TABLE_NAME, REFERENCED_TABLE_NAME
FROM INFORMATION_SCHEMA.KEY_COLUMN_USAGE
WHERE TABLE_SCHEMA = 'micro_ecommerce' AND REFERENCED_TABLE_NAME IS NOT NULL;
```

## Troubleshooting

### Issue: "Migration already applied" error

**Solution:**
```bash
# With Flyway - check history
mvn flyway:info

# If you need to reset (CAUTION - removes all data)
mvn flyway:clean
mvn flyway:migrate
```

### Issue: Foreign Key Constraint Error

**Cause:** Table creation order is wrong
**Solution:** Migrations create tables in correct order:
1. customers
2. inventory  
3. orders (references customers)
4. payments (references orders)

### Issue: "Cannot load JDBC driver class"

**Solution:** Ensure JDBC driver is in classpath:
```xml
<dependency>
    <groupId>com.mysql</groupId>
    <artifactId>mysql-connector-java</artifactId>
    <version>8.0.33</version>
</dependency>
```

### Issue: Timezone Errors

**Solution:** Always use UTC:
```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/micro_ecommerce?serverTimezone=UTC
```

### Issue: "Access denied for user"

**Solution:** Verify database user and password:
```bash
mysql -u root -p -e "SHOW GRANTS FOR 'ecommerce_user'@'localhost';"

# Grant privileges if needed
mysql -u root -p -e "GRANT ALL ON micro_ecommerce.* TO 'ecommerce_user'@'localhost';"
```

## Adding New Migrations

When making schema changes:

1. **Create new migration file**
   ```bash
   # V3__Add_New_Table.sql
   ```

2. **Write idempotent SQL**
   ```sql
   CREATE TABLE IF NOT EXISTS new_table (
       id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
       ...
   );
   ```

3. **Test thoroughly**
   ```bash
   # Test on staging
   mvn flyway:migrate
   # Verify
   mvn flyway:info
   ```

4. **Deploy to production**
   ```bash
   # Backup first!
   mysqldump -u root -p micro_ecommerce > backup_pre_v3.sql
   # Run migration
   mvn flyway:migrate
   ```

## Local Development Setup

For local development, use the automated setup script:

```bash
# From project root
./scripts/local-setup.sh full

# This will:
# 1. Build all services
# 2. Start Docker containers
# 3. Spring Boot services will auto-run migrations
```

Or manually:
```bash
# Start MySQL via Docker
docker run -d \
  -e MYSQL_ROOT_PASSWORD=password \
  -e MYSQL_DATABASE=micro_ecommerce \
  -p 3306:3306 \
  mysql:8.0

# Wait for MySQL to be ready
sleep 10

# Run migrations
mvn flyway:migrate

# Verify
mysql -u root -p -e "USE micro_ecommerce; SHOW TABLES;"
```

## Production Deployment Checklist

- [ ] Backup production database
- [ ] Test migrations on staging environment
- [ ] Review all migration files
- [ ] Verify database connectivity
- [ ] Set environment variables correctly
- [ ] Monitor migration execution
- [ ] Verify all tables created successfully
- [ ] Check data integrity
- [ ] Verify application startup
- [ ] Run smoke tests
- [ ] Monitor application logs for errors

## Related Documentation

- [Database Schema Documentation](db/README.md)
- [Saga Pattern Guide](SAGA_PATTERN_GUIDE.md)
- [Local Infrastructure Setup](LOCAL_INFRASTRUCTURE_SETUP.md)
- [Architecture Documentation](ARCHITECTURE.md)

## Support Resources

- [Flyway Documentation](https://flywaydb.org/documentation/)
- [PostgreSQL Documentation](https://www.postgresql.org/docs/)
