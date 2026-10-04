-- Same identity-desync bug as order-service's H2 variant (see its
-- V7__Sync_Orders_Id_Sequence.sql) - V2's seed data inserts explicit IDs
-- without advancing H2's identity counter.
ALTER TABLE inventory ALTER COLUMN id RESTART WITH (SELECT COALESCE(MAX(id), 0) + 1 FROM inventory);
