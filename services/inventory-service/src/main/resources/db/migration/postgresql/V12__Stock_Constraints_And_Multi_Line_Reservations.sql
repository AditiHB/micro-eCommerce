-- Stock can reach exactly zero (the last unit is sellable) but never go below it - the database says so, not
-- just the application. Reservations are per (order, product) so an order can hold several lines.

ALTER TABLE inventory ADD CONSTRAINT chk_inventory_quantity_non_negative CHECK (quantity >= 0);

ALTER TABLE inventory_reservations DROP CONSTRAINT inventory_reservations_order_id_key;
ALTER TABLE inventory_reservations ADD CONSTRAINT uq_reservation_order_product UNIQUE (order_id, product_id);
ALTER TABLE inventory_reservations ADD CONSTRAINT chk_reservation_quantity_positive CHECK (quantity > 0);
CREATE INDEX idx_inventory_reservations_order_id ON inventory_reservations (order_id);
