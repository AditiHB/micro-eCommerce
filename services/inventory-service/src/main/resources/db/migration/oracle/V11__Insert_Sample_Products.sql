-- Inventory Service - Oracle Sample Data: same sample catalogue product-service used to seed (SKU-001..SKU-008).

INSERT INTO products (id, name, description, price, currency, sku, category)
VALUES (seq_products.NEXTVAL, 'Wireless Headphones', 'High-quality Bluetooth wireless headphones with noise cancellation', 79.99, 'USD', 'SKU-001', 'Electronics');

INSERT INTO products (id, name, description, price, currency, sku, category)
VALUES (seq_products.NEXTVAL, 'USB-C Cable', '6ft USB-C charging and data cable', 12.99, 'USD', 'SKU-002', 'Electronics');

INSERT INTO products (id, name, description, price, currency, sku, category)
VALUES (seq_products.NEXTVAL, 'Cotton T-Shirt', 'Comfortable 100% cotton t-shirt available in multiple colors', 19.99, 'USD', 'SKU-003', 'Clothing');

INSERT INTO products (id, name, description, price, currency, sku, category)
VALUES (seq_products.NEXTVAL, 'Running Shoes', 'Professional running shoes with cushioning and support', 89.99, 'USD', 'SKU-004', 'Clothing');

INSERT INTO products (id, name, description, price, currency, sku, category)
VALUES (seq_products.NEXTVAL, 'Java Programming Book', 'Comprehensive guide to Java programming for beginners and advanced users', 49.99, 'USD', 'SKU-005', 'Books');

INSERT INTO products (id, name, description, price, currency, sku, category)
VALUES (seq_products.NEXTVAL, 'Desk Lamp', 'LED desk lamp with adjustable brightness and color temperature', 34.99, 'USD', 'SKU-006', 'Home');

INSERT INTO products (id, name, description, price, currency, sku, category)
VALUES (seq_products.NEXTVAL, 'Coffee Maker', 'Programmable coffee maker with thermal carafe', 59.99, 'USD', 'SKU-007', 'Home');

INSERT INTO products (id, name, description, price, currency, sku, category)
VALUES (seq_products.NEXTVAL, 'Smartphone Case', 'Protective case for smartphones with shock absorption', 24.99, 'USD', 'SKU-008', 'Electronics');

COMMIT;
