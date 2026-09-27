# Database Migration Scripts

This directory contains SQL migration scripts for the micro-eCommerce database schema and sample data.

## Overview

The migrations follow the **Flyway naming convention** and are compatible with multiple opensource migration tools:
- **Flyway** - Java-based, most popular
- **Liquibase** - Java-based, with XML/YAML/JSON support
- **golang-migrate** - Golang-based, lightweight
- **Migrate** (any tool that supports versioned migrations)

## Migration Files

### V1__Create_Initial_Schema.sql
**DDL (Data Definition Language)**
- Creates 4 tables: `customers`, `inventory`, `orders`, `payments`
- Defines primary keys, foreign keys, and indexes
- Sets up constraints and relationships
- Tables use InnoDB engine with UTF-8 charset for compatibility

#### Schema Overview

| Table | Purpose | Key Columns |
|-------|---------|-------------|
| `customers` | Store customer information | id, name, email |
| `inventory` | Track product stock levels | id, product_id, quantity |
| `orders` | Record orders with saga status | id, customer_id, product_id, quantity, status |
| `payments` | Track payments with saga status | id, order_id, amount, status |

### V2__Insert_Sample_Data.sql
**DML (Data Manipulation Language)**
- Inserts 5 sample customers
- Inserts 8 sample products with inventory
- Inserts 5 sample orders at different saga stages
- Inserts 5 sample payments at different statuses
- Uses `ON DUPLICATE KEY UPDATE` for idempotent operations

## Running Migrations

### Using Flyway

#### Maven Integration
Add to `pom.xml`:
```xml
<dependency>
    <groupId>org.flywaydb</groupId>
    <artifactId>flyway-core</artifactId>
    <version>9.20.0</version>
</dependency>
```

Configure `application.yml`:
```yaml
spring:
  flyway:
    enabled: true
    locations: classpath:db/migration
    schemas: your_database
    user: ${DB_USERNAME}
    password: ${DB_PASSWORD}
```

Run migrations:
```bash
# Maven
mvn flyway:migrate

# Or let Spring Boot run them on startup
mvn spring-boot:run

# Check migration history
mvn flyway:info
```

### Using Liquibase

Create master changelog `db/changelog/db.changelog-master.yaml`:
```yaml
databaseChangeLog:
  - include:
      file: migration/V1__Create_Initial_Schema.sql
  - include:
      file: migration/V2__Insert_Sample_Data.sql
```

Add to `pom.xml`:
```xml
<dependency>
    <groupId>org.liquibase</groupId>
    <artifactId>liquibase-core</artifactId>
    <version>4.20.0</version>
</dependency>
```

Configure `application.yml`:
```yaml
spring:
  liquibase:
    enabled: true
    change-log: classpath:db/changelog/db.changelog-master.yaml
```

### Using golang-migrate

```bash
# Install
brew install golang-migrate  # macOS
# or download from: https://github.com/golang-migrate/migrate

# Run migrations
migrate -path db/migration -database "mysql://user:password@tcp(localhost:3306)/dbname" up

# Rollback
migrate -path db/migration -database "mysql://user:password@tcp(localhost:3306)/dbname" down
```

### Manual SQL Execution

If using manual SQL execution (not recommended for production):
```bash
# MySQL
mysql -u username -p database_name < db/migration/V1__Create_Initial_Schema.sql
mysql -u username -p database_name < db/migration/V2__Insert_Sample_Data.sql

# PostgreSQL
psql -U username -d database_name -f db/migration/V1__Create_Initial_Schema.sql
psql -U username -d database_name -f db/migration/V2__Insert_Sample_Data.sql
```

## Database Requirements

- **MySQL 5.7+** or **PostgreSQL 10+** or compatible database
- Character set: **UTF-8 (utf8mb4)** recommended
- Timezone: **UTC** (all timestamps stored in UTC)

## Saga Pattern Status Values

### OrderStatus
- `PENDING` - Order created, awaiting inventory reservation
- `INVENTORY_RESERVED` - Inventory has been reserved
- `PAYMENT_PROCESSING` - Payment is being processed
- `COMPLETED` - Order successfully completed
- `CANCELLED` - Order cancelled (compensation triggered)
- `FAILED` - Order failed (compensation triggered)

### PaymentStatus
- `PENDING` - Payment awaiting processing
- `PROCESSING` - Payment is being processed
- `PROCESSED` - Payment successful
- `FAILED` - Payment failed (triggers compensation)
- `REFUNDED` - Payment refunded (compensating transaction)

## Indexes

Indexes are created on frequently queried columns:
- `customers.email` - For login/lookup
- `inventory.product_id` - For product queries
- `orders.customer_id` - For customer orders
- `orders.product_id` - For inventory checks
- `orders.status` - For saga state queries
- `payments.order_id` - For payment lookup
- `payments.status` - For compensation queries

## Foreign Keys

Relationships enforce data integrity:
- `orders.customer_id` → `customers.id` (CASCADE DELETE)
- `payments.order_id` → `orders.id` (CASCADE DELETE)

## Verifying Migrations

After running migrations, verify schema:

```sql
-- Check tables created
SHOW TABLES;

-- Check table structure
DESCRIBE customers;
DESCRIBE inventory;
DESCRIBE orders;
DESCRIBE payments;

-- Verify data insertion
SELECT COUNT(*) FROM customers;
SELECT COUNT(*) FROM inventory;
SELECT COUNT(*) FROM orders;
SELECT COUNT(*) FROM payments;

-- Check constraints and indexes
SHOW INDEXES FROM orders;
SHOW CREATE TABLE orders\G
```

## Environment Configuration

Set database connection details:

```bash
# MySQL via environment
export SPRING_DATASOURCE_URL=jdbc:mysql://localhost:3306/micro_ecommerce
export SPRING_DATASOURCE_USERNAME=root
export SPRING_DATASOURCE_PASSWORD=password
export SPRING_JPA_DATABASE_PLATFORM=org.hibernate.dialect.MySQL8Dialect
```

Or in `application-local.yml`:
```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/micro_ecommerce?serverTimezone=UTC&useUnicode=true&characterEncoding=utf8
    username: root
    password: password
    driver-class-name: com.mysql.cj.jdbc.Driver
  jpa:
    hibernate:
      ddl-auto: validate  # Use 'validate' with Flyway to prevent conflicts
    properties:
      hibernate:
        dialect: org.hibernate.dialect.MySQL8Dialect
        format_sql: true
        use_sql_comments: true
```

## Troubleshooting

### Migration Already Applied Error
If you get "Migration already applied" but need to re-run:

```bash
# With Flyway
mvn flyway:clean  # CAUTION: Removes all migrations!
mvn flyway:migrate
```

### Foreign Key Constraint Error
Ensure tables are created in correct order:
1. `customers` table first
2. `inventory` table
3. `orders` table (references customers)
4. `payments` table (references orders)

This order is maintained in the migration files.

### Timestamp/Timezone Issues
- All timestamps use UTC
- Ensure database timezone is set to UTC
- Java applications should use `LocalDateTime` (timezone-agnostic)

## Production Considerations

1. **Backup Before Migration**
   ```bash
   mysqldump -u user -p database > backup_$(date +%Y%m%d_%H%M%S).sql
   ```

2. **Test Migrations First**
   - Run on a staging environment
   - Verify application works with new schema

3. **Monitor Performance**
   - Check slow query logs after migration
   - Verify indexes are being used

4. **Keep Migration History**
   - Never modify old migration files
   - Always create new migration files for changes
   - Maintains schema version consistency

## Additional Resources

- [Flyway Documentation](https://flywaydb.org/documentation/)
- [Liquibase Documentation](https://docs.liquibase.com/)
- [golang-migrate](https://github.com/golang-migrate/migrate)
- [Saga Pattern Guide](../SAGA_PATTERN_GUIDE.md)
- [Architecture Documentation](../ARCHITECTURE.md)
