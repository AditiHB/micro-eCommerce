-- Payment Service - Oracle Sample Data

INSERT INTO payments (id, order_id, amount, status, created_at, updated_at)
VALUES (1, 1, 299.99, 'PROCESSED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO payments (id, order_id, amount, status, created_at, updated_at)
VALUES (2, 2, 99.99, 'PENDING', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO payments (id, order_id, amount, status, created_at, updated_at)
VALUES (3, 3, 149.97, 'PROCESSING', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO payments (id, order_id, amount, status, created_at, updated_at)
VALUES (4, 4, 199.99, 'PROCESSING', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO payments (id, order_id, amount, status, created_at, updated_at)
VALUES (5, 5, 199.98, 'PROCESSED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

COMMIT;
