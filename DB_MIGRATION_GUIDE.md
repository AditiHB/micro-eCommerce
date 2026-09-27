# Database Migration Guide

## Overview

This guide explains how to use the SQL migration scripts included in the `db/migration` directory to set up and manage the micro-eCommerce database schema.

The migration scripts are compatible with popular opensource database migration tools:
- **Flyway** (Recommended for Java/Spring Boot)
- **Liquibase** (Alternative for Spring Boot)
- **golang-migrate** (Lightweight, language-agnostic)
- **Manual SQL execution** (Not recommended for production)

## Quick Start

### Prerequisites
- MySQL 5.7+ or PostgreSQL 10+
- Database client installed
- One of the migration tools listed above

### Option 1: Flyway (Recommended for Spring Boot)

1. **Add Flyway Dependency**
   ```xml
   <!-- Add to pom.xml -->
   <dependency>
       <groupId>org.flywaydb</groupId>
       <artifactId>flyway-core</artifactId>
       <version>9.22.3</version>
   </dependency>
   ```

2. **Configure Spring Boot** (`application.yml` or `application-local.yml`)
   ```yaml
   spring:
     datasource:
       url: jdbc:mysql://localhost:3306/micro_ecommerce?serverTimezone=UTC&useUnicode=true&characterEncoding=utf8
       username: root
       password: password
       driver-class-name: com.mysql.cj.jdbc.Driver
     jpa:
       hibernate:
         ddl-auto: validate  # Important: Use 'validate' with Flyway
       properties:
         hibernate:
           dialect: org.hibernate.dialect.MySQL8Dialect
     flyway:
       enabled: true
       locations: classpath:db/migration
       baseline-on-migrate: true
   ```

3. **Copy Migration Files**
   ```bash
   mkdir -p src/main/resources/db/migration
   cp db/migration/*.sql src/main/resources/db/migration/
   ```

4. **Run Migrations**
   ```bash
   # Spring Boot will run migrations automatically on startup
   mvn spring-boot:run
   
   # Or run manually
   mvn flyway:migrate
   
   # Check migration history
   mvn flyway:info
   
   # Validate without applying
   mvn flyway:validate
   ```

### Option 2: Liquibase

1. **Add Liquibase Dependency**
   ```xml
   <dependency>
       <groupId>org.liquibase</groupId>
       <artifactId>liquibase-core</artifactId>
       <version>4.23.0</version>
   </dependency>
   ```

2. **Create Master Changelog** (`src/main/resources/db/changelog/db.changelog-master.yaml`)
   ```yaml
   databaseChangeLog:
     - sqlFile:
         dbms: mysql
         path: db/migration/V1__Create_Initial_Schema.sql
         relativeToChangelogFile: false
     - sqlFile:
         dbms: mysql
         path: db/migration/V2__Insert_Sample_Data.sql
         relativeToChangelogFile: false
   ```

3. **Configure Spring Boot**
   ```yaml
   spring:
     datasource:
       url: jdbc:mysql://localhost:3306/micro_ecommerce?serverTimezone=UTC
       username: root
       password: password
     liquibase:
       enabled: true
       change-log: classpath:db/changelog/db.changelog-master.yaml
   ```

4. **Run Migrations**
   ```bash
   # Spring Boot runs them on startup
   mvn spring-boot:run
   
   # Check status
   mvn liquibase:status
   
   # Generate SQL without applying
   mvn liquibase:update-sql
   ```

### Option 3: golang-migrate

1. **Install golang-migrate**
   ```bash
   # macOS
   brew install golang-migrate
   
   # Linux (download binary)
   wget https://github.com/golang-migrate/migrate/releases/download/v4.17.0/migrate.linux-amd64.tar.gz
   tar xvzf migrate.linux-amd64.tar.gz
   sudo mv migrate /usr/local/bin/
   ```

2. **Run Migrations**
   ```bash
   # Apply all migrations
   migrate -path db/migration \
     -database "mysql://root:password@tcp(localhost:3306)/micro_ecommerce" \
     up
   
   # Check version
   migrate -path db/migration \
     -database "mysql://root:password@tcp(localhost:3306)/micro_ecommerce" \
     version
   
   # Rollback (down)
   migrate -path db/migration \
     -database "mysql://root:password@tcp(localhost:3306)/micro_ecommerce" \
     down
   ```

### Option 4: Manual SQL Execution

```bash
# Create database first
mysql -u root -p -e "CREATE DATABASE IF NOT EXISTS micro_ecommerce"

# Apply migrations
mysql -u root -p micro_ecommerce < db/migration/V1__Create_Initial_Schema.sql
mysql -u root -p micro_ecommerce < db/migration/V2__Insert_Sample_Data.sql

# Verify
mysql -u root -p micro_ecommerce -e "SHOW TABLES; SELECT COUNT(*) as customer_count FROM customers;"
```

## Migration Files

### V1__Create_Initial_Schema.sql
**DDL (Data Definition Language) - Creates Database Schema**

Creates 4 tables with proper structure for the saga pattern:

```
customers
├── id (BIGINT, PRIMARY KEY)
├── name (VARCHAR 100)
├── email (VARCHAR 255, UNIQUE)
├── created_at (TIMESTAMP)
└── updated_at (TIMESTAMP)

inventory
├── id (BIGINT, PRIMARY KEY)
├── product_id (VARCHAR 50, UNIQUE)
├── quantity (INT)
├── created_at (TIMESTAMP)
└── updated_at (TIMESTAMP)

orders
├── id (BIGINT, PRIMARY KEY)
├── customer_id (BIGINT, FK → customers)
├── product_id (VARCHAR 50)
├── quantity (INT)
├── status (VARCHAR 50) - PENDING, INVENTORY_RESERVED, PAYMENT_PROCESSING, COMPLETED, CANCELLED, FAILED
├── created_at (TIMESTAMP)
└── updated_at (TIMESTAMP)

payments
├── id (BIGINT, PRIMARY KEY)
├── order_id (BIGINT, FK → orders)
├── amount (DECIMAL 10,2)
├── status (VARCHAR 50) - PENDING, PROCESSING, PROCESSED, FAILED, REFUNDED
├── created_at (TIMESTAMP)
└── updated_at (TIMESTAMP)
```

**Indexes:**
- Foreign key indexes for performance
- Status indexes for saga queries
- Timestamp indexes for time-range queries
- Unique indexes for lookups

**Constraints:**
- NOT NULL on required fields
- Foreign key relationships with CASCADE DELETE
- UNIQUE constraints where needed

### V2__Insert_Sample_Data.sql
**DML (Data Manipulation Language) - Inserts Test Data**

Populates initial data for testing:
- 5 sample customers
- 8 products with varying inventory levels
- 5 sample orders at different saga stages
- 5 sample payments at different statuses

Uses `ON DUPLICATE KEY UPDATE` for idempotency - safe to run multiple times.

### U1__Undo_Create_Initial_Schema.sql
**Undo Script - Removes All Tables**

Rollback script for development/testing. Drops tables in proper order to handle foreign keys.

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
- Flyway: `V{version}__{description}.sql` (e.g., `V1__Create_Initial_Schema.sql`)
- Undo: `U{version}__{description}.sql` (e.g., `U1__Undo_Create_Initial_Schema.sql`)
- Use double underscore to separate version and description

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
   # U3__Undo_Add_New_Table.sql
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
- [Liquibase Documentation](https://docs.liquibase.com/)
- [golang-migrate GitHub](https://github.com/golang-migrate/migrate)
- [MySQL Documentation](https://dev.mysql.com/doc/)
- [PostgreSQL Documentation](https://www.postgresql.org/docs/)
