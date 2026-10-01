-- Product Service - Oracle: add optimistic-locking version column
ALTER TABLE products ADD version NUMBER(19) DEFAULT 0 NOT NULL;
