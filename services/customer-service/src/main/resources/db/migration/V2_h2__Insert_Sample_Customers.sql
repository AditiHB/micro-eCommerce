-- Customer Service - H2 Sample Data
-- Idempotent inserts using ON DUPLICATE KEY UPDATE

INSERT INTO customers (id, name, email, created_at, updated_at)
VALUES
    (1, 'John Doe', 'john.doe@example.com', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (2, 'Jane Smith', 'jane.smith@example.com', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (3, 'Robert Johnson', 'robert.johnson@example.com', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (4, 'Alice Williams', 'alice.williams@example.com', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (5, 'Michael Brown', 'michael.brown@example.com', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
ON DUPLICATE KEY UPDATE
    name = VALUES(name),
    email = VALUES(email),
    updated_at = CURRENT_TIMESTAMP;
