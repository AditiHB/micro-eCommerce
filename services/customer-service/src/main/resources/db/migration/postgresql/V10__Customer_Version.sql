-- Version column for optimistic locking, exposed as the ETag so a stale update can be refused (If-Match).

ALTER TABLE customers ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
