-- Inventory Service - PostgreSQL: add optimistic-locking version column
ALTER TABLE inventory ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;
