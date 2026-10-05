-- A payment moves through PENDING -> AUTHORIZED -> CAPTURED -> REFUNDED (or FAILED) with guarded transitions.
-- The processor's own reference and the decline reason are kept so a refund or a support query can use them.

ALTER TABLE payments ADD COLUMN currency            VARCHAR(3)   NOT NULL DEFAULT 'USD';
ALTER TABLE payments ADD COLUMN customer_id         BIGINT;
ALTER TABLE payments ADD COLUMN processor_reference VARCHAR(100);
ALTER TABLE payments ADD COLUMN failure_reason      VARCHAR(500);
ALTER TABLE payments ADD COLUMN version             BIGINT       NOT NULL DEFAULT 0;

UPDATE payments SET status = 'CAPTURED' WHERE status = 'PROCESSED';
UPDATE payments SET status = 'PENDING'  WHERE status = 'PROCESSING';

ALTER TABLE payments ADD CONSTRAINT chk_payments_status
    CHECK (status IN ('PENDING', 'AUTHORIZED', 'CAPTURED', 'FAILED', 'REFUNDED'));
