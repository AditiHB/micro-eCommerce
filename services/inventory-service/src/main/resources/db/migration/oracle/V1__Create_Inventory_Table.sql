-- Inventory Service - Oracle Database Schema
-- Oracle Dialect: Uses SEQUENCE for auto-increment, PL/SQL triggers for updated_at

-- Create sequence for auto-increment
BEGIN
    EXECUTE IMMEDIATE 'DROP SEQUENCE seq_inventory';
EXCEPTION
    WHEN OTHERS THEN
        IF SQLCODE != -2289 THEN
            RAISE;
        END IF;
END;
/

CREATE SEQUENCE seq_inventory
    START WITH 1
    INCREMENT BY 1
    NOCYCLE
    NOCACHE;
/

CREATE TABLE inventory (
    id NUMBER(19) PRIMARY KEY,
    product_id VARCHAR2(50) NOT NULL UNIQUE,
    quantity NUMBER(10) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL
);

CREATE INDEX idx_inventory_product_id ON inventory(product_id);
CREATE INDEX idx_inventory_created_at ON inventory(created_at);

-- Trigger to populate id from sequence on INSERT
CREATE OR REPLACE TRIGGER inventory_insert_id
BEFORE INSERT ON inventory
FOR EACH ROW
BEGIN
    IF :NEW.id IS NULL THEN
        SELECT seq_inventory.NEXTVAL INTO :NEW.id FROM dual;
    END IF;
END;
/

-- Trigger to update updated_at on UPDATE
CREATE OR REPLACE TRIGGER inventory_update_timestamp
BEFORE UPDATE ON inventory
FOR EACH ROW
BEGIN
    :NEW.updated_at := CURRENT_TIMESTAMP;
END;
/
