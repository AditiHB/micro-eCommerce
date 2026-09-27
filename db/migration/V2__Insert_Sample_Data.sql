-- ============================================================================
-- Micro-eCommerce Sample Data
-- Version 2: Insert Initial/Static Data
-- Description: Populates sample data for testing the microservices flow
-- ============================================================================

-- ============================================================================
-- Insert Sample Customers
-- ============================================================================
INSERT INTO customers (id, name, email, created_at, updated_at) VALUES
(1, 'John Doe', 'john.doe@example.com', NOW(), NOW()),
(2, 'Jane Smith', 'jane.smith@example.com', NOW(), NOW()),
(3, 'Robert Johnson', 'robert.johnson@example.com', NOW(), NOW()),
(4, 'Alice Williams', 'alice.williams@example.com', NOW(), NOW()),
(5, 'Michael Brown', 'michael.brown@example.com', NOW(), NOW())
ON DUPLICATE KEY UPDATE updated_at = NOW();

-- ============================================================================
-- Insert Sample Inventory
-- Products represent available items for sale
-- ============================================================================
INSERT INTO inventory (id, product_id, quantity, created_at, updated_at) VALUES
(1, 'PROD-001', 100, NOW(), NOW()),
(2, 'PROD-002', 50, NOW(), NOW()),
(3, 'PROD-003', 75, NOW(), NOW()),
(4, 'PROD-004', 200, NOW(), NOW()),
(5, 'PROD-005', 30, NOW(), NOW()),
(6, 'PROD-006', 150, NOW(), NOW()),
(7, 'PROD-007', 80, NOW(), NOW()),
(8, 'PROD-008', 120, NOW(), NOW())
ON DUPLICATE KEY UPDATE updated_at = NOW();

-- ============================================================================
-- Insert Sample Orders
-- These orders demonstrate the saga pattern flow:
-- PENDING -> INVENTORY_RESERVED -> PAYMENT_PROCESSING -> COMPLETED
-- OR: PENDING -> CANCELLED (if any step fails)
-- ============================================================================
INSERT INTO orders (id, customer_id, product_id, quantity, status, created_at, updated_at) VALUES
(1, 1, 'PROD-001', 5, 'COMPLETED', NOW(), NOW()),
(2, 2, 'PROD-002', 3, 'COMPLETED', NOW(), NOW()),
(3, 3, 'PROD-003', 2, 'PENDING', NOW(), NOW()),
(4, 4, 'PROD-004', 1, 'INVENTORY_RESERVED', NOW(), NOW()),
(5, 5, 'PROD-005', 4, 'PAYMENT_PROCESSING', NOW(), NOW())
ON DUPLICATE KEY UPDATE updated_at = NOW();

-- ============================================================================
-- Insert Sample Payments
-- Payment statuses follow the order saga pattern
-- PENDING -> PROCESSING -> PROCESSED
-- OR: FAILED/REFUNDED (if compensation triggered)
-- ============================================================================
INSERT INTO payments (id, order_id, amount, status, created_at, updated_at) VALUES
(1, 1, 250.50, 'PROCESSED', NOW(), NOW()),
(2, 2, 150.00, 'PROCESSED', NOW(), NOW()),
(3, 3, 99.99, 'PENDING', NOW(), NOW()),
(4, 4, 49.99, 'PROCESSING', NOW(), NOW()),
(5, 5, 199.95, 'PROCESSING', NOW(), NOW())
ON DUPLICATE KEY UPDATE updated_at = NOW();

-- ============================================================================
-- Verification Queries (for testing, not part of migration)
-- Uncomment to verify data insertion:
-- SELECT 'Customers' as table_name, COUNT(*) as count FROM customers
-- UNION ALL
-- SELECT 'Inventory', COUNT(*) FROM inventory
-- UNION ALL
-- SELECT 'Orders', COUNT(*) FROM orders
-- UNION ALL
-- SELECT 'Payments', COUNT(*) FROM payments;
-- ============================================================================
