-- Event Sourcing - Oracle Database Schema
-- Oracle Dialect: Uses SEQUENCE for auto-increment

-- Create sequence for auto-increment
BEGIN
    EXECUTE IMMEDIATE 'DROP SEQUENCE seq_event_store';
EXCEPTION
    WHEN OTHERS THEN
        IF SQLCODE != -2289 THEN
            RAISE;
        END IF;
END;
/

CREATE SEQUENCE seq_event_store
    START WITH 1
    INCREMENT BY 1
    NOCYCLE
    NOCACHE;
/

CREATE TABLE event_store (
    id NUMBER(19) PRIMARY KEY,
    event_id VARCHAR2(100) NOT NULL UNIQUE,
    aggregate_id VARCHAR2(50) NOT NULL,
    aggregate_type VARCHAR2(50) NOT NULL,
    event_type VARCHAR2(100) NOT NULL,
    event_data CLOB NOT NULL,
    version NUMBER(10) NOT NULL,
    occurred_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    stored_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    metadata VARCHAR2(255),
    correlation_id CLOB,
    causation_id CLOB
);

CREATE INDEX idx_event_store_aggregate_id ON event_store(aggregate_id);
CREATE INDEX idx_event_store_aggregate_type ON event_store(aggregate_type);
CREATE INDEX idx_event_store_event_type ON event_store(event_type);
CREATE INDEX idx_event_store_occurred_at ON event_store(occurred_at);

-- Trigger to populate id from sequence on INSERT
CREATE OR REPLACE TRIGGER event_store_insert_id
BEFORE INSERT ON event_store
FOR EACH ROW
BEGIN
    IF :NEW.id IS NULL THEN
        SELECT seq_event_store.NEXTVAL INTO :NEW.id FROM dual;
    END IF;
END;
/
