package com.ecommerce.orderservice;

import com.ecommerce.common.testsupport.PostgresIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** The whole application starts against a real PostgreSQL, and every migration applies cleanly. */
@SpringBootTest
@PostgresIntegrationTest
@DisplayName("Order service application")
class OrderServiceApplicationTests {

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    @DisplayName("the context loads: the entities match the migrated schema (Hibernate validate) and all beans wire")
    void contextLoads() {
        assertThat(jdbc.queryForObject("SELECT count(*) FROM flyway_schema_history WHERE success = false", Long.class)).isZero();
    }

    @Test
    @DisplayName("the schema holds the order aggregate and the messaging tables")
    void schema() {
        List<String> tables = jdbc.queryForList(
                "SELECT table_name FROM information_schema.tables WHERE table_schema = 'public'", String.class);

        assertThat(tables).contains("orders", "order_lines", "idempotency_keys", "outbox_event", "processed_events",
                "dead_letters", "event_store");
        assertThat(tables).as("the retired per-service users table").doesNotContain("users");
    }
}
