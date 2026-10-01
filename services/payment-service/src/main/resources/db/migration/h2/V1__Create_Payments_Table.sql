-- Payment Service - H2 Database Schema
-- H2 Dialect: Uses IDENTITY for auto-increment
-- Note: Stores order_id as reference, but no foreign key constraint
-- (order data is owned by Order Service, communication via Kafka)

CREATE TABLE IF NOT EXISTS payments (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    order_id BIGINT NOT NULL,
    amount DECIMAL(10, 2) NOT NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP);


CREATE INDEX idx_order_id ON payments(order_id);
CREATE INDEX idx_status ON payments(status);
CREATE INDEX idx_created_at ON payments(created_at);
