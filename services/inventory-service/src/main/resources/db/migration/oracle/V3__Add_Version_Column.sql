-- Inventory Service - Oracle: add optimistic-locking version column
ALTER TABLE inventory ADD version NUMBER(19) DEFAULT 0 NOT NULL;
