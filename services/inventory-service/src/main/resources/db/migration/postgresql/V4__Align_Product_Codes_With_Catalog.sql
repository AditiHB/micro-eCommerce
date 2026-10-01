-- Inventory Service - PostgreSQL: align sample product codes with product-service's
-- actual SKUs (product-service seeds SKU-001..SKU-008; this table used an
-- unrelated PROD-00X scheme, so the two services' sample data didn't refer
-- to the same products even nominally).
UPDATE inventory SET product_id = 'SKU-001' WHERE product_id = 'PROD-001';
UPDATE inventory SET product_id = 'SKU-002' WHERE product_id = 'PROD-002';
UPDATE inventory SET product_id = 'SKU-003' WHERE product_id = 'PROD-003';
UPDATE inventory SET product_id = 'SKU-004' WHERE product_id = 'PROD-004';
UPDATE inventory SET product_id = 'SKU-005' WHERE product_id = 'PROD-005';
UPDATE inventory SET product_id = 'SKU-006' WHERE product_id = 'PROD-006';
UPDATE inventory SET product_id = 'SKU-007' WHERE product_id = 'PROD-007';
UPDATE inventory SET product_id = 'SKU-008' WHERE product_id = 'PROD-008';
