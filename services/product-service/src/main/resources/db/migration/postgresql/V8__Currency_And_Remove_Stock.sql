-- The catalogue prices things in a currency, and no longer counts stock: inventory-service is the single
-- owner of stock levels.

ALTER TABLE products ADD COLUMN currency VARCHAR(3) NOT NULL DEFAULT 'USD';
ALTER TABLE products DROP COLUMN quantity_available;
