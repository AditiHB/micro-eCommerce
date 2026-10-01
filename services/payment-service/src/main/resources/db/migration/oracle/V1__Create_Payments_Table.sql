-- Payment Service - Oracle Database Schema
-- Oracle Dialect: Uses SEQUENCE for auto-increment, PL/SQL triggers for updated_at
-- Note: Stores order_id as reference, but no foreign key constraint
-- (order data is owned by Order Service, communication via Kafka)

-- Create sequence for auto-increment
BEGIN
    EXECUTE IMMEDIATE 'DROP SEQUENCE seq_payments';
EXCEPTION
    WHEN OTHERS THEN
        IF SQLCODE != -2289 THEN
            RAISE;
        END IF;
END;
/

CREATE SEQUENCE seq_payments
    START WITH 1
    INCREMENT BY 1
    NOCYCLE
    NOCACHE;
/

CREATE TABLE payments (
    id NUMBER(19) PRIMARY KEY,
    order_id NUMBER(19) NOT NULL,
    amount NUMBER(10, 2) NOT NULL,
    status VARCHAR2(50) DEFAULT 'PENDING' NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL
);

CREATE INDEX idx_payments_order_id ON payments(order_id);
CREATE INDEX idx_payments_status ON payments(status);
CREATE INDEX idx_payments_created_at ON payments(created_at);

-- Trigger to populate id from sequence on INSERT
CREATE OR REPLACE TRIGGER payments_insert_id
BEFORE INSERT ON payments
FOR EACH ROW
BEGIN
    IF :NEW.id IS NULL THEN
        SELECT seq_payments.NEXTVAL INTO :NEW.id FROM dual;
    END IF;
END;
/

-- Trigger to update updated_at on UPDATE
CREATE OR REPLACE TRIGGER payments_update_timestamp
BEFORE UPDATE ON payments
FOR EACH ROW
BEGIN
    :NEW.updated_at := CURRENT_TIMESTAMP;
END;
/
