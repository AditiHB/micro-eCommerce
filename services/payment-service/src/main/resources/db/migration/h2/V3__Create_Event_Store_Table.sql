-- Event Sourcing - H2 Database Schema
-- H2 Dialect: Uses IDENTITY for auto-increment

CREATE TABLE IF NOT EXISTS event_store (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    event_id VARCHAR(100) NOT NULL UNIQUE,
    aggregate_id VARCHAR(50) NOT NULL,
    aggregate_type VARCHAR(50) NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    event_data VARCHAR(1000000) NOT NULL,
    version INT NOT NULL,
    occurred_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    stored_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    metadata VARCHAR(255),
    correlation_id VARCHAR(1000000),
    causation_id VARCHAR(1000000));


CREATE INDEX idx_aggregate_id ON event_store(aggregate_id);
CREATE INDEX idx_aggregate_type ON event_store(aggregate_type);
CREATE INDEX idx_event_type ON event_store(event_type);
CREATE INDEX idx_occurred_at ON event_store(occurred_at);
