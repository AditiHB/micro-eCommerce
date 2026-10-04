-- Idempotency ledger for the saga's reserve/release path (see
-- InventoryReservation.java). A row's existence is what lets
-- InventoryEventListener tell a genuine first delivery of order-created
-- apart from a redelivery of the same event - without this, a Kafka
-- redelivery (a normal occurrence, not a failure) would decrement the same
-- order's stock twice.

CREATE TABLE IF NOT EXISTS inventory_reservations (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    order_id BIGINT NOT NULL UNIQUE,
    product_id VARCHAR(50) NOT NULL,
    quantity INT NOT NULL,
    released_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_inventory_reservations_product_id ON inventory_reservations(product_id);
