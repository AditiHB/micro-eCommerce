-- Order Service - PostgreSQL Sample Data

INSERT INTO orders (id, customer_id, product_id, quantity, status, created_at, updated_at)
VALUES
    (1, 1, 'PROD-001', 2, 'COMPLETED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (2, 2, 'PROD-002', 1, 'PENDING', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (3, 3, 'PROD-003', 3, 'INVENTORY_RESERVED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (4, 4, 'PROD-004', 1, 'PAYMENT_PROCESSING', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (5, 5, 'PROD-005', 2, 'COMPLETED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
ON CONFLICT (id) DO UPDATE
SET
    customer_id = EXCLUDED.customer_id,
    product_id = EXCLUDED.product_id,
    quantity = EXCLUDED.quantity,
    status = EXCLUDED.status,
    updated_at = CURRENT_TIMESTAMP;
