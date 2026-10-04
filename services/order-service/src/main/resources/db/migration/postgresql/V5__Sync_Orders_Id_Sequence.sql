-- V2 inserts sample orders with explicit ids, which does not advance the
-- identity sequence backing the id column. Without this, the first INSERT
-- that lets the database generate an id collides with one of those explicit
-- values and fails with a duplicate key violation.
SELECT setval(pg_get_serial_sequence('orders', 'id'), COALESCE((SELECT MAX(id) FROM orders), 1));
