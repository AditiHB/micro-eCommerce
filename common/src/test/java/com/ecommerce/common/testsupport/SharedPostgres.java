package com.ecommerce.common.testsupport;

import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * One real PostgreSQL for every integration test in a module's JVM. Production runs on PostgreSQL only, so the
 * tests do too - with the real Flyway migrations and Hibernate in {@code validate} mode, which is what proves the
 * entities and the schema agree. (An in-memory H2 "compatible" mode proves nothing about Postgres.)
 *
 * <p>Use it through {@link PostgresIntegrationTest}.
 */
public final class SharedPostgres {

    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    static {
        POSTGRES.start(); // stopped by Testcontainers' Ryuk when the JVM exits
    }

    private SharedPostgres() {
    }

    public static PostgreSQLContainer<?> container() {
        return POSTGRES;
    }

    public static class Initializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {
        @Override
        public void initialize(ConfigurableApplicationContext context) {
            TestPropertyValues.of(
                    "spring.datasource.url=" + POSTGRES.getJdbcUrl(),
                    "spring.datasource.username=" + POSTGRES.getUsername(),
                    "spring.datasource.password=" + POSTGRES.getPassword(),
                    "spring.datasource.driver-class-name=org.postgresql.Driver",
                    "spring.jpa.database-platform=org.hibernate.dialect.PostgreSQLDialect",
                    "spring.jpa.hibernate.ddl-auto=validate",
                    // Services point Flyway at an owner role when one is configured; here owner == app role.
                    "spring.flyway.user=" + POSTGRES.getUsername(),
                    "spring.flyway.password=" + POSTGRES.getPassword(),
                    "spring.flyway.enabled=true"
            ).applyTo(context);
        }
    }
}
