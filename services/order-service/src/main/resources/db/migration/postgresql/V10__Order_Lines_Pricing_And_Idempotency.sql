-- An order is now an aggregate: priced lines, a total and currency snapshotted from the catalogue when it was
-- placed, a version for optimistic locking, and a status that can only hold a known value.
-- Idempotency keys make "create order" safe to retry.

ALTER TABLE orders ADD COLUMN total_amount NUMERIC(12, 2) NOT NULL DEFAULT 0;
ALTER TABLE orders ADD COLUMN currency     VARCHAR(3)     NOT NULL DEFAULT 'USD';
ALTER TABLE orders ADD COLUMN version      BIGINT         NOT NULL DEFAULT 0;

CREATE TABLE order_lines (
    id         BIGSERIAL PRIMARY KEY,
    order_id   BIGINT         NOT NULL REFERENCES orders (id) ON DELETE CASCADE,
    line_no    INTEGER        NOT NULL,
    product_id VARCHAR(100)   NOT NULL,
    quantity   INTEGER        NOT NULL CHECK (quantity > 0),
    unit_price NUMERIC(12, 2) NOT NULL CHECK (unit_price >= 0),
    CONSTRAINT uq_order_lines_order_line UNIQUE (order_id, line_no)
);
CREATE INDEX idx_order_lines_product_id ON order_lines (product_id);

-- Orders that existed before pricing become one-line orders. Their price was never recorded, so it is 0.
INSERT INTO order_lines (order_id, line_no, product_id, quantity, unit_price)
SELECT id, 1, product_id, quantity, 0 FROM orders;

ALTER TABLE orders DROP COLUMN product_id;
ALTER TABLE orders DROP COLUMN quantity;

ALTER TABLE orders ADD CONSTRAINT chk_orders_status
    CHECK (status IN ('PENDING', 'INVENTORY_RESERVED', 'PAYMENT_PROCESSING', 'COMPLETED', 'CANCELLED', 'FAILED'));

-- Lets the saga-deadline reaper find stuck orders without scanning the table.
CREATE INDEX idx_orders_open_updated_at ON orders (updated_at)
    WHERE status IN ('PENDING', 'INVENTORY_RESERVED', 'PAYMENT_PROCESSING');

CREATE TABLE idempotency_keys (
    id              BIGSERIAL PRIMARY KEY,
    principal       VARCHAR(200) NOT NULL,
    idempotency_key VARCHAR(200) NOT NULL,
    request_hash    VARCHAR(64)  NOT NULL,
    order_id        BIGINT       NOT NULL REFERENCES orders (id) ON DELETE CASCADE,
    created_at      TIMESTAMPTZ  NOT NULL,
    CONSTRAINT uq_idempotency_principal_key UNIQUE (principal, idempotency_key)
);
CREATE INDEX idx_idempotency_created_at ON idempotency_keys (created_at);
