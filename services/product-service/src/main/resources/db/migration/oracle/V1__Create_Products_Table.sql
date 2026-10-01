-- Product Service - Oracle Database Schema
-- Oracle Dialect: Uses SEQUENCE for auto-increment, VARCHAR2 for variable-length strings

BEGIN
  EXECUTE IMMEDIATE 'DROP SEQUENCE products_seq';
EXCEPTION WHEN OTHERS THEN
  NULL;
END;
/

CREATE SEQUENCE products_seq START WITH 1 INCREMENT BY 1 NOCACHE;

BEGIN
  EXECUTE IMMEDIATE 'DROP TABLE products';
EXCEPTION WHEN OTHERS THEN
  NULL;
END;
/

CREATE TABLE products (
    id NUMBER PRIMARY KEY,
    name VARCHAR2(255) NOT NULL,
    description CLOB,
    price DECIMAL(19, 2) NOT NULL,
    sku VARCHAR2(100) NOT NULL UNIQUE,
    category VARCHAR2(100) NOT NULL,
    quantity_available NUMBER DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL
);

CREATE INDEX idx_products_sku ON products(sku);
CREATE INDEX idx_products_category ON products(category);
CREATE INDEX idx_products_created_at ON products(created_at);

BEGIN
  EXECUTE IMMEDIATE 'DROP SEQUENCE product_categories_seq';
EXCEPTION WHEN OTHERS THEN
  NULL;
END;
/

CREATE SEQUENCE product_categories_seq START WITH 1 INCREMENT BY 1 NOCACHE;

BEGIN
  EXECUTE IMMEDIATE 'DROP TABLE product_categories';
EXCEPTION WHEN OTHERS THEN
  NULL;
END;
/

CREATE TABLE product_categories (
    id NUMBER PRIMARY KEY,
    name VARCHAR2(100) NOT NULL UNIQUE,
    description VARCHAR2(500),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL
);

CREATE INDEX idx_categories_name ON product_categories(name);

CREATE OR REPLACE TRIGGER products_insert
BEFORE INSERT ON products
FOR EACH ROW
BEGIN
  IF :NEW.id IS NULL THEN
    :NEW.id := products_seq.NEXTVAL;
  END IF;
END;
/

CREATE OR REPLACE TRIGGER product_categories_insert
BEFORE INSERT ON product_categories
FOR EACH ROW
BEGIN
  IF :NEW.id IS NULL THEN
    :NEW.id := product_categories_seq.NEXTVAL;
  END IF;
END;
/
