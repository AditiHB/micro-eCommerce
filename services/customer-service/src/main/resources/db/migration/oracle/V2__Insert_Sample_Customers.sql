-- Customer Service - Oracle Sample Data
-- Individual INSERT statements with explicit COMMIT

INSERT INTO customers (id, name, email, created_at, updated_at)
VALUES (1, 'John Doe', 'john.doe@example.com', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO customers (id, name, email, created_at, updated_at)
VALUES (2, 'Jane Smith', 'jane.smith@example.com', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO customers (id, name, email, created_at, updated_at)
VALUES (3, 'Robert Johnson', 'robert.johnson@example.com', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO customers (id, name, email, created_at, updated_at)
VALUES (4, 'Alice Williams', 'alice.williams@example.com', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO customers (id, name, email, created_at, updated_at)
VALUES (5, 'Michael Brown', 'michael.brown@example.com', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

COMMIT;
