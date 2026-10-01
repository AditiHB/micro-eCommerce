-- Inventory Service - H2 Database Schema
-- H2 Dialect: Uses IDENTITY for auto-increment

CREATE TABLE IF NOT EXISTS inventory (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    product_id VARCHAR(50) NOT NULL UNIQUE,
    quantity INT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP);


CREATE INDEX idx_product_id ON inventory(product_id);
CREATE INDEX idx_created_at ON inventory(created_at);
