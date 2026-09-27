-- Insert sample product categories
INSERT INTO product_categories (name, description) VALUES
('Electronics', 'Electronic devices and gadgets'),
('Clothing', 'Apparel and fashion items'),
('Books', 'Books and reading materials'),
('Home', 'Home and garden products');

-- Insert sample products
INSERT INTO products (name, description, price, sku, category, quantity_available) VALUES
('Wireless Headphones', 'High-quality Bluetooth wireless headphones with noise cancellation', 79.99, 'SKU-001', 'Electronics', 50),
('USB-C Cable', '6ft USB-C charging and data cable', 12.99, 'SKU-002', 'Electronics', 100),
('Cotton T-Shirt', 'Comfortable 100% cotton t-shirt available in multiple colors', 19.99, 'SKU-003', 'Clothing', 75),
('Running Shoes', 'Professional running shoes with cushioning and support', 89.99, 'SKU-004', 'Clothing', 40),
('Java Programming Book', 'Comprehensive guide to Java programming for beginners and advanced users', 49.99, 'SKU-005', 'Books', 30),
('Desk Lamp', 'LED desk lamp with adjustable brightness and color temperature', 34.99, 'SKU-006', 'Home', 45),
('Coffee Maker', 'Programmable coffee maker with thermal carafe', 59.99, 'SKU-007', 'Home', 25),
('Smartphone Case', 'Protective case for smartphones with shock absorption', 24.99, 'SKU-008', 'Electronics', 120);
