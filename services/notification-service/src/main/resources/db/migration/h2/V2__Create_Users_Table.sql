-- Notification Service - H2 Database Schema
-- H2 Dialect: Uses IDENTITY for auto-increment, simple TIMESTAMP handling
--
-- Same users table as every other service - needed so this service's own
-- JwtAuthenticationFilter/CustomUserDetailsService (common.security) can
-- validate a request's JWT against a local copy of the user, consistent
-- with the rest of the app's "database per service" design.

CREATE TABLE IF NOT EXISTS users (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(255) NOT NULL UNIQUE,
    email VARCHAR(255) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL,
    role VARCHAR(50) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP);


CREATE INDEX idx_users_email ON users(email);
CREATE INDEX idx_users_username ON users(username);
