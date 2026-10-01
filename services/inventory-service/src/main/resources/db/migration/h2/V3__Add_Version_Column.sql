-- Inventory Service - H2: add optimistic-locking version column
-- Closes a lost-update race in reserve/release (concurrent requests could
-- both read the same quantity and both succeed, overselling stock).
ALTER TABLE inventory ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;
