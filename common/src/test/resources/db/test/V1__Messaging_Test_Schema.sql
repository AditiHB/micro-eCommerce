-- Schema for the common module's own integration tests: the tables the shared messaging code owns.
-- Each service creates the same tables through its own migrations (V*__Create_Messaging_Tables.sql).

CREATE TABLE event_store (
    id              BIGSERIAL PRIMARY KEY,
    event_id        VARCHAR(100) NOT NULL UNIQUE,
    aggregate_id    VARCHAR(50)  NOT NULL,
    aggregate_type  VARCHAR(50)  NOT NULL,
    event_type      VARCHAR(100) NOT NULL,
    event_data      TEXT         NOT NULL,
    version         INTEGER      NOT NULL,
    occurred_at     TIMESTAMP    NOT NULL,
    stored_at       TIMESTAMP    NOT NULL,
    metadata        VARCHAR(255),
    correlation_id  TEXT,
    causation_id    TEXT
);

CREATE TABLE outbox_event (
    id              BIGSERIAL PRIMARY KEY,
    event_id        VARCHAR(100) NOT NULL UNIQUE,
    topic           VARCHAR(200) NOT NULL,
    message_key     VARCHAR(100) NOT NULL,
    event_type      VARCHAR(100) NOT NULL,
    aggregate_id    VARCHAR(100) NOT NULL,
    aggregate_type  VARCHAR(100) NOT NULL,
    correlation_id  VARCHAR(100),
    causation_id    VARCHAR(100),
    payload         TEXT         NOT NULL,
    status          VARCHAR(20)  NOT NULL,
    attempts        INTEGER      NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMPTZ  NOT NULL,
    last_error      VARCHAR(1000),
    created_at      TIMESTAMPTZ  NOT NULL,
    published_at    TIMESTAMPTZ
);
CREATE INDEX idx_outbox_pending ON outbox_event (id) WHERE status = 'PENDING';
CREATE INDEX idx_outbox_aggregate_pending ON outbox_event (aggregate_type, aggregate_id) WHERE status = 'PENDING';

CREATE TABLE processed_events (
    consumer     VARCHAR(100) NOT NULL,
    event_id     VARCHAR(100) NOT NULL,
    processed_at TIMESTAMPTZ  NOT NULL,
    PRIMARY KEY (consumer, event_id)
);

CREATE TABLE dead_letters (
    id                BIGSERIAL PRIMARY KEY,
    original_topic    VARCHAR(200) NOT NULL,
    original_partition INTEGER,
    original_offset   BIGINT,
    message_key       VARCHAR(200),
    payload           TEXT,
    headers           TEXT,
    consumer_group    VARCHAR(100) NOT NULL,
    exception_class   VARCHAR(300),
    exception_message VARCHAR(1000),
    status            VARCHAR(20)  NOT NULL,
    received_at       TIMESTAMPTZ  NOT NULL,
    replayed_at       TIMESTAMPTZ
);
CREATE INDEX idx_dead_letters_status ON dead_letters (status);
