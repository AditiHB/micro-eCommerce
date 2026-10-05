-- Messaging infrastructure shared by every service (see docs/EVENT_CONTRACTS.md and docs/RESILIENCE.md):
--   outbox_event     - events written in the same transaction as the business change, relayed to Kafka afterwards
--   processed_events - the idempotent-consumer ledger: which events this service has already applied
--   dead_letters     - messages this service could not process after its retries, parked for an operator

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
    published_at    TIMESTAMPTZ,
    CONSTRAINT chk_outbox_status CHECK (status IN ('PENDING', 'PUBLISHED', 'DEAD'))
);

-- The relay only ever reads pending rows, so index only those (the table is mostly published history).
CREATE INDEX idx_outbox_pending ON outbox_event (id) WHERE status = 'PENDING';
CREATE INDEX idx_outbox_aggregate_pending ON outbox_event (aggregate_type, aggregate_id) WHERE status = 'PENDING';
CREATE INDEX idx_outbox_published_at ON outbox_event (published_at) WHERE status = 'PUBLISHED';

CREATE TABLE processed_events (
    consumer     VARCHAR(100) NOT NULL,
    event_id     VARCHAR(100) NOT NULL,
    processed_at TIMESTAMPTZ  NOT NULL,
    PRIMARY KEY (consumer, event_id)
);
CREATE INDEX idx_processed_events_processed_at ON processed_events (processed_at);

CREATE TABLE dead_letters (
    id                 BIGSERIAL PRIMARY KEY,
    original_topic     VARCHAR(200) NOT NULL,
    original_partition INTEGER,
    original_offset    BIGINT,
    message_key        VARCHAR(200),
    payload            TEXT,
    headers            TEXT,
    consumer_group     VARCHAR(100) NOT NULL,
    exception_class    VARCHAR(300),
    exception_message  VARCHAR(1000),
    status             VARCHAR(20)  NOT NULL,
    received_at        TIMESTAMPTZ  NOT NULL,
    replayed_at        TIMESTAMPTZ,
    CONSTRAINT chk_dead_letters_status CHECK (status IN ('PARKED', 'REPLAYED'))
);
CREATE INDEX idx_dead_letters_status ON dead_letters (status);
