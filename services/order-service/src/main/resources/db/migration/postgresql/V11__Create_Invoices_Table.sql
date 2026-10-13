-- One generated invoice per COMPLETED order: three compressed copies of the same PDF (gzip, zstd,
-- brotli - the batch job that fills this table demonstrates all three codecs) plus the signature over
-- the canonical, uncompressed PDF bytes, so verification never depends on which compressed variant is
-- at hand. BYTEA is used here deliberately (unlike the rest of this repo, which avoids blob columns)
-- because these are genuinely binary artifacts, not encoded text.
CREATE TABLE invoices (
    id             BIGSERIAL PRIMARY KEY,
    order_id       BIGINT NOT NULL UNIQUE REFERENCES orders (id) ON DELETE CASCADE,
    customer_id    BIGINT NOT NULL,
    pdf_gzip       BYTEA NOT NULL,
    pdf_zstd       BYTEA NOT NULL,
    pdf_brotli     BYTEA NOT NULL,
    signature      BYTEA NOT NULL,
    signing_key_id VARCHAR(100) NOT NULL,
    content_sha256 VARCHAR(64) NOT NULL,
    generated_at   TIMESTAMPTZ NOT NULL,
    status         VARCHAR(20) NOT NULL DEFAULT 'GENERATED'
);
CREATE INDEX idx_invoices_order_id ON invoices (order_id);
