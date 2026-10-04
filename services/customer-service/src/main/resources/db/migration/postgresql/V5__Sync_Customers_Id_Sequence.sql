-- V2 inserts sample customers with explicit ids, which does not advance the
-- identity sequence backing the id column. Without this, the first INSERT
-- that lets the database generate an id collides with one of those explicit
-- values and fails with a duplicate key violation.
SELECT setval(pg_get_serial_sequence('customers', 'id'), COALESCE((SELECT MAX(id) FROM customers), 1));
