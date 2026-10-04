-- Idempotency ledger for the saga's reserve/release path (see
-- InventoryReservation.java). A row's existence is what lets
-- InventoryEventListener tell a genuine first delivery of order-created
-- apart from a redelivery of the same event - without this, a Kafka
-- redelivery (a normal occurrence, not a failure) would decrement the same
-- order's stock twice.

BEGIN
    EXECUTE IMMEDIATE 'DROP SEQUENCE seq_inventory_reservations';
EXCEPTION
    WHEN OTHERS THEN
        IF SQLCODE != -2289 THEN
            RAISE;
        END IF;
END;
/

CREATE SEQUENCE seq_inventory_reservations
    START WITH 1
    INCREMENT BY 1
    NOCYCLE
    NOCACHE;
/

CREATE TABLE inventory_reservations (
    id NUMBER(19) PRIMARY KEY,
    order_id NUMBER(19) NOT NULL UNIQUE,
    product_id VARCHAR2(50) NOT NULL,
    quantity NUMBER(10) NOT NULL,
    released_at TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL
);

CREATE INDEX idx_inventory_reservations_product_id ON inventory_reservations(product_id);

CREATE OR REPLACE TRIGGER inventory_reservations_insert_id
BEFORE INSERT ON inventory_reservations
FOR EACH ROW
BEGIN
    IF :NEW.id IS NULL THEN
        SELECT seq_inventory_reservations.NEXTVAL INTO :NEW.id FROM dual;
    END IF;
END;
/
