-- Event Sourcing - H2 Database Schema
-- H2 Dialect: Uses IDENTITY for auto-increment

CREATE TABLE IF NOT EXISTS event_store (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    event_id VARCHAR(100) NOT NULL UNIQUE,
    aggregate_id VARCHAR(50) NOT NULL,
    aggregate_type VARCHAR(50) NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    event_data CLOB NOT NULL,
    version INT NOT NULL,
    occurred_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    stored_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    metadata VARCHAR(255),
    correlation_id CLOB,
    causation_id CLOB,

    INDEX idx_aggregate_id (aggregate_id),
    INDEX idx_aggregate_type (aggregate_type),
    INDEX idx_event_type (event_type),
    INDEX idx_occurred_at (occurred_at)
);
