-- Product Service - PostgreSQL: add optimistic-locking version column
ALTER TABLE products ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;
