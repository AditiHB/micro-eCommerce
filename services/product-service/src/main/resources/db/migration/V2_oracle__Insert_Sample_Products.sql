-- Insert sample product categories
INSERT INTO product_categories (id, name, description) VALUES
(product_categories_seq.NEXTVAL, 'Electronics', 'Electronic devices and gadgets');
INSERT INTO product_categories (id, name, description) VALUES
(product_categories_seq.NEXTVAL, 'Clothing', 'Apparel and fashion items');
INSERT INTO product_categories (id, name, description) VALUES
(product_categories_seq.NEXTVAL, 'Books', 'Books and reading materials');
INSERT INTO product_categories (id, name, description) VALUES
(product_categories_seq.NEXTVAL, 'Home', 'Home and garden products');

-- Insert sample products
INSERT INTO products (id, name, description, price, sku, category, quantity_available) VALUES
(products_seq.NEXTVAL, 'Wireless Headphones', 'High-quality Bluetooth wireless headphones with noise cancellation', 79.99, 'SKU-001', 'Electronics', 50);
INSERT INTO products (id, name, description, price, sku, category, quantity_available) VALUES
(products_seq.NEXTVAL, 'USB-C Cable', '6ft USB-C charging and data cable', 12.99, 'SKU-002', 'Electronics', 100);
INSERT INTO products (id, name, description, price, sku, category, quantity_available) VALUES
(products_seq.NEXTVAL, 'Cotton T-Shirt', 'Comfortable 100% cotton t-shirt available in multiple colors', 19.99, 'SKU-003', 'Clothing', 75);
INSERT INTO products (id, name, description, price, sku, category, quantity_available) VALUES
(products_seq.NEXTVAL, 'Running Shoes', 'Professional running shoes with cushioning and support', 89.99, 'SKU-004', 'Clothing', 40);
INSERT INTO products (id, name, description, price, sku, category, quantity_available) VALUES
(products_seq.NEXTVAL, 'Java Programming Book', 'Comprehensive guide to Java programming for beginners and advanced users', 49.99, 'SKU-005', 'Books', 30);
INSERT INTO products (id, name, description, price, sku, category, quantity_available) VALUES
(products_seq.NEXTVAL, 'Desk Lamp', 'LED desk lamp with adjustable brightness and color temperature', 34.99, 'SKU-006', 'Home', 45);
INSERT INTO products (id, name, description, price, sku, category, quantity_available) VALUES
(products_seq.NEXTVAL, 'Coffee Maker', 'Programmable coffee maker with thermal carafe', 59.99, 'SKU-007', 'Home', 25);
INSERT INTO products (id, name, description, price, sku, category, quantity_available) VALUES
(products_seq.NEXTVAL, 'Smartphone Case', 'Protective case for smartphones with shock absorption', 24.99, 'SKU-008', 'Electronics', 120);

COMMIT;
