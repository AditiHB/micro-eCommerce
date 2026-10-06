-- Inventory Service - H2 Sample Data: same sample catalogue product-service used to seed (SKU-001..SKU-008).
INSERT INTO products (name, description, price, currency, sku, category) VALUES
('Wireless Headphones', 'High-quality Bluetooth wireless headphones with noise cancellation', 79.99, 'USD', 'SKU-001', 'Electronics'),
('USB-C Cable', '6ft USB-C charging and data cable', 12.99, 'USD', 'SKU-002', 'Electronics'),
('Cotton T-Shirt', 'Comfortable 100% cotton t-shirt available in multiple colors', 19.99, 'USD', 'SKU-003', 'Clothing'),
('Running Shoes', 'Professional running shoes with cushioning and support', 89.99, 'USD', 'SKU-004', 'Clothing'),
('Java Programming Book', 'Comprehensive guide to Java programming for beginners and advanced users', 49.99, 'USD', 'SKU-005', 'Books'),
('Desk Lamp', 'LED desk lamp with adjustable brightness and color temperature', 34.99, 'USD', 'SKU-006', 'Home'),
('Coffee Maker', 'Programmable coffee maker with thermal carafe', 59.99, 'USD', 'SKU-007', 'Home'),
('Smartphone Case', 'Protective case for smartphones with shock absorption', 24.99, 'USD', 'SKU-008', 'Electronics');
