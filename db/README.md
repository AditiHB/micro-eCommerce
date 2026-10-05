# Database Migrations

This project uses **Flyway** for schema migrations. There is no manual "migrate" command to run: each microservice runs its own Flyway migrations automatically on startup, via Spring Boot's Flyway auto-configuration (`spring.flyway.enabled: true` in each service's `application.yml`).

## How it actually works in this repo

Every microservice owns its **own database** and its own migration files - there is no shared/monolithic schema:

| Service | Database | Migration files |
|---|---|---|
| customer-service | `customer_db` | [services/customer-service/src/main/resources/db/migration](../services/customer-service/src/main/resources/db/migration) |
| inventory-service | `inventory_db` | [services/inventory-service/src/main/resources/db/migration](../services/inventory-service/src/main/resources/db/migration) |
| order-service | `order_db` | [services/order-service/src/main/resources/db/migration](../services/order-service/src/main/resources/db/migration) |
| payment-service | `payment_db` | [services/payment-service/src/main/resources/db/migration](../services/payment-service/src/main/resources/db/migration) |
| notification-service | `notification_db` | [services/notification-service/src/main/resources/db/migration](../services/notification-service/src/main/resources/db/migration) |
| product-service | `product_db` | [services/product-service/src/main/resources/db/migration](../services/product-service/src/main/resources/db/migration) |

Each service's migration directory has a subfolder per database vendor - `h2/`, `oracle/`, `postgresql/` - and Spring Boot picks the right one at runtime via the `{vendor}` placeholder in `spring.flyway.locations` (`classpath:db/migration/{vendor}`). Filenames follow Flyway's standard versioned convention: `V1__Create_X_Table.sql`, `V2__...sql`, etc.

## When migrations run

**Automatically, every time a service starts.** Flyway runs before Hibernate/JPA touches the schema (`spring.jpa.hibernate.ddl-auto: validate` - JPA only validates against what Flyway already created, it never creates or alters tables itself).

- **Default (H2 in-memory):** every service boots against its own throwaway H2 database with no setup required. Flyway creates the schema fresh each time the container starts.
- **Against real PostgreSQL:** start the stack with the `postgres` profile so Flyway runs against it instead:
  ```bash
  docker compose --profile postgres --env-file .env.postgres up -d
  ```
  See [docs/SETUP_AND_DEPLOYMENT.md](../docs/SETUP_AND_DEPLOYMENT.md) for details. If you only need the bare database (e.g. to inspect it, no services), run `docker compose --profile postgres --env-file .env.postgres up -d postgres` instead.

If you need to run Flyway outside of starting the whole application (e.g. to preview pending migrations), use the Maven plugin from the specific service module:
```bash
mvn -pl services/customer-service flyway:info
mvn -pl services/customer-service flyway:migrate
```

## Users and credentials (none in the databases)

Services no longer keep a `users` table and no migration seeds an account. Identity lives in Keycloak
([docs/KEYCLOAK_IDENTITY.md](../docs/KEYCLOAK_IDENTITY.md)); development users and a test client are created
there by `infrastructure/keycloak/seed-dev.sh`, with passwords from your git-ignored `.env`
(`scripts/gen-env.sh`).

History, because Flyway never edits an applied version: the earlier `V*__Create_Users_Table.sql`,
`V*__Seed_Test_Users.sql` and `V*__Seed_Service_Account.sql` migrations are left as they were, and a new
`V*__Drop_Users_Table.sql` in every service/vendor removes the table - and with it the seeded `karate_admin` and
`notification-service-account` rows - on every database, including ones that already applied the old versions.

## Verifying migrations

```bash
# List tables in a service's database (example: customer_db via the postgres container)
docker exec -e PGPASSWORD="$CUSTOMER_DB_PASSWORD" postgres psql -U customer_owner -d customer_db -c "\dt"

# Check Flyway's own migration history
docker exec -e PGPASSWORD="$CUSTOMER_DB_PASSWORD" postgres psql -U customer_owner -d customer_db -c "SELECT * FROM flyway_schema_history;"
```

## Troubleshooting

**"Migration checksum mismatch" / Flyway refuses to start:** an already-applied migration file was edited after the fact. Never modify a migration file once it has run anywhere; add a new `V{n+1}__...sql` file instead.

**Schema validation failure on startup (`Schema-validation: missing table [...]`):** Flyway didn't create the table Hibernate expected. Usually means a migration file isn't being picked up - check it's in the right vendor subfolder and follows the `V<version>__<description>.sql` naming convention exactly (Flyway silently skips files that don't match).

## Adding a new migration

1. Add a new `V{n+1}__Description.sql` file to the relevant service's `db/migration/<vendor>/` folder (one per vendor you support).
2. Never edit or renumber an existing migration file - Flyway tracks what's already been applied by checksum.
3. Restart the service; Flyway applies it automatically on the next startup.
