-- V2's seed data inserts explicit IDs 1-5 without advancing H2's identity
-- counter, so the first auto-generated INSERT (e.g. via POST /api/orders)
-- collides with an existing seeded row ("Unique index or primary key
-- violation"). Same root cause as postgresql/V6__Sync_Orders_Id_Sequence.sql -
-- H2 just also needs its own fix since the fresh-per-container-restart
-- H2 database masked it until something created more than a couple of orders
-- in a single run (e.g. repeated local/E2E testing).
ALTER TABLE orders ALTER COLUMN id RESTART WITH (SELECT COALESCE(MAX(id), 0) + 1 FROM orders);
