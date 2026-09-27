-- Customer Service - Oracle Database Schema
-- Oracle Dialect: Uses SEQUENCE for auto-increment, PL/SQL triggers for updated_at

-- Create sequence for auto-increment
BEGIN
    EXECUTE IMMEDIATE 'DROP SEQUENCE seq_customers';
EXCEPTION
    WHEN OTHERS THEN
        IF SQLCODE != -2289 THEN
            RAISE;
        END IF;
END;
/

CREATE SEQUENCE seq_customers
    START WITH 1
    INCREMENT BY 1
    NOCYCLE
    NOCACHE;
/

CREATE TABLE customers (
    id NUMBER(19) PRIMARY KEY,
    name VARCHAR2(100) NOT NULL,
    email VARCHAR2(255) NOT NULL UNIQUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL
);

CREATE INDEX idx_customers_email ON customers(email);
CREATE INDEX idx_customers_created_at ON customers(created_at);

-- Trigger to populate id from sequence on INSERT
CREATE OR REPLACE TRIGGER customers_insert_id
BEFORE INSERT ON customers
FOR EACH ROW
BEGIN
    IF :NEW.id IS NULL THEN
        SELECT seq_customers.NEXTVAL INTO :NEW.id FROM dual;
    END IF;
END;
/

-- Trigger to update updated_at on UPDATE
CREATE OR REPLACE TRIGGER customers_update_timestamp
BEFORE UPDATE ON customers
FOR EACH ROW
BEGIN
    :NEW.updated_at := CURRENT_TIMESTAMP;
END;
/
