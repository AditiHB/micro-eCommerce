-- Order Service - Oracle Database Schema
-- Oracle Dialect: Uses SEQUENCE for auto-increment, PL/SQL triggers for updated_at
-- Note: Stores customer_id as reference, but no foreign key constraint
-- (customer data is owned by Customer Service, communication via Kafka)

-- Create sequence for auto-increment
BEGIN
    EXECUTE IMMEDIATE 'DROP SEQUENCE seq_orders';
EXCEPTION
    WHEN OTHERS THEN
        IF SQLCODE != -2289 THEN
            RAISE;
        END IF;
END;
/

CREATE SEQUENCE seq_orders
    START WITH 1
    INCREMENT BY 1
    NOCYCLE
    NOCACHE;
/

CREATE TABLE orders (
    id NUMBER(19) PRIMARY KEY,
    customer_id NUMBER(19) NOT NULL,
    product_id VARCHAR2(50) NOT NULL,
    quantity NUMBER(10) NOT NULL,
    status VARCHAR2(50) DEFAULT 'PENDING' NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL
);

CREATE INDEX idx_orders_customer_id ON orders(customer_id);
CREATE INDEX idx_orders_product_id ON orders(product_id);
CREATE INDEX idx_orders_status ON orders(status);
CREATE INDEX idx_orders_created_at ON orders(created_at);

-- Trigger to populate id from sequence on INSERT
CREATE OR REPLACE TRIGGER orders_insert_id
BEFORE INSERT ON orders
FOR EACH ROW
BEGIN
    IF :NEW.id IS NULL THEN
        SELECT seq_orders.NEXTVAL INTO :NEW.id FROM dual;
    END IF;
END;
/

-- Trigger to update updated_at on UPDATE
CREATE OR REPLACE TRIGGER orders_update_timestamp
BEFORE UPDATE ON orders
FOR EACH ROW
BEGIN
    :NEW.updated_at := CURRENT_TIMESTAMP;
END;
/
