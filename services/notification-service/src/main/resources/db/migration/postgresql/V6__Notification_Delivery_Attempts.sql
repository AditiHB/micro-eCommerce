-- A notification is recorded as PENDING in the same transaction as the event that caused it, and delivered
-- afterwards with retries, so a mail-server outage never loses it or blocks the event stream.

ALTER TABLE notifications ADD COLUMN attempts        INTEGER     NOT NULL DEFAULT 0;
ALTER TABLE notifications ADD COLUMN next_attempt_at TIMESTAMPTZ;

UPDATE notifications SET next_attempt_at = now() WHERE status = 'PENDING';

CREATE INDEX idx_notifications_due ON notifications (next_attempt_at) WHERE status = 'PENDING';
