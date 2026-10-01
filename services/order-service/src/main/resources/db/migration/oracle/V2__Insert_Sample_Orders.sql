-- Order Service - Oracle Sample Data

INSERT INTO orders (id, customer_id, product_id, quantity, status, created_at, updated_at)
VALUES (1, 1, 'PROD-001', 2, 'COMPLETED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO orders (id, customer_id, product_id, quantity, status, created_at, updated_at)
VALUES (2, 2, 'PROD-002', 1, 'PENDING', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO orders (id, customer_id, product_id, quantity, status, created_at, updated_at)
VALUES (3, 3, 'PROD-003', 3, 'INVENTORY_RESERVED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO orders (id, customer_id, product_id, quantity, status, created_at, updated_at)
VALUES (4, 4, 'PROD-004', 1, 'PAYMENT_PROCESSING', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO orders (id, customer_id, product_id, quantity, status, created_at, updated_at)
VALUES (5, 5, 'PROD-005', 2, 'COMPLETED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

COMMIT;
