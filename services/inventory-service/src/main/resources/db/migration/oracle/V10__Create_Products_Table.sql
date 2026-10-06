-- Inventory Service - Oracle: the catalogue, merged in from product-service. No stock column - inventory-service
-- already owns stock exclusively in the `inventory` table, keyed by this table's SKU.
-- Oracle Dialect: Uses SEQUENCE for auto-increment, PL/SQL trigger for updated_at.

CREATE SEQUENCE seq_products
    START WITH 1
    INCREMENT BY 1
    NOCYCLE
    NOCACHE;
/

CREATE TABLE products (
    id NUMBER(19) PRIMARY KEY,
    name VARCHAR2(255) NOT NULL,
    description CLOB,
    price NUMBER(19, 2) NOT NULL,
    currency VARCHAR2(3) DEFAULT 'USD' NOT NULL,
    sku VARCHAR2(100) NOT NULL UNIQUE,
    category VARCHAR2(100) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    version NUMBER(19) DEFAULT 0 NOT NULL
);

CREATE INDEX idx_products_sku ON products(sku);
CREATE INDEX idx_products_category ON products(category);
CREATE INDEX idx_products_created_at ON products(created_at);

CREATE OR REPLACE TRIGGER products_insert_id
BEFORE INSERT ON products
FOR EACH ROW
BEGIN
    IF :NEW.id IS NULL THEN
        SELECT seq_products.NEXTVAL INTO :NEW.id FROM dual;
    END IF;
END;
/

CREATE OR REPLACE TRIGGER products_update_timestamp
BEFORE UPDATE ON products
FOR EACH ROW
BEGIN
    :NEW.updated_at := CURRENT_TIMESTAMP;
END;
/
