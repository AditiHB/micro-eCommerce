-- Customer Service - PostgreSQL Sample Data
-- Idempotent inserts using ON CONFLICT DO UPDATE

INSERT INTO customers (id, name, email, created_at, updated_at)
VALUES
    (1, 'John Doe', 'john.doe@example.com', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (2, 'Jane Smith', 'jane.smith@example.com', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (3, 'Robert Johnson', 'robert.johnson@example.com', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (4, 'Alice Williams', 'alice.williams@example.com', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (5, 'Michael Brown', 'michael.brown@example.com', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
ON CONFLICT (email) DO UPDATE
SET
    name = EXCLUDED.name,
    updated_at = CURRENT_TIMESTAMP;
