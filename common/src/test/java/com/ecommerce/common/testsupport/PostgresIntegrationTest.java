package com.ecommerce.common.testsupport;

import org.junit.jupiter.api.Tag;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a test that needs the real database: it runs against a Testcontainers PostgreSQL (see
 * {@link SharedPostgres}), the {@code test} profile, and in the integration tier of the build.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Tag("integration")
@ActiveProfiles("test")
@ContextConfiguration(initializers = SharedPostgres.Initializer.class)
public @interface PostgresIntegrationTest {
}
