package com.ecommerce.notificationservice;

import com.ecommerce.common.testsupport.PostgresIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/** The whole application starts against a real PostgreSQL, and every migration applies cleanly. */
@SpringBootTest
@PostgresIntegrationTest
@DisplayName("Notification service application")
class NotificationServiceApplicationTests {

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    @DisplayName("the context loads: entities match the migrated schema and all beans wire")
    void contextLoads() {
        assertThat(jdbc.queryForObject("SELECT count(*) FROM flyway_schema_history WHERE success = false", Long.class)).isZero();
    }
}
