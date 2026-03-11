-- Outbox events table (Transactional Outbox Pattern)
-- Every domain event is written here in the same transaction as the state change.
-- The OutboxPoller reads PENDING events and publishes them to Kafka.
CREATE TABLE outbox_events (
    id              UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    aggregate_id    VARCHAR(255)    NOT NULL,
    aggregate_type  VARCHAR(100)    NOT NULL DEFAULT 'PaymentIntent',
    event_type      VARCHAR(100)    NOT NULL,
    payload         JSONB           NOT NULL,
    status          VARCHAR(20)     NOT NULL DEFAULT 'PENDING', -- PENDING | PUBLISHED | FAILED
    retry_count     INT             NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    published_at    TIMESTAMPTZ
);

-- Poller queries: pending events ordered by creation time
CREATE INDEX idx_outbox_status_created ON outbox_events(status, created_at ASC)
    WHERE status = 'PENDING';

-- Aggregate event log lookup
CREATE INDEX idx_outbox_aggregate_id ON outbox_events(aggregate_id);

-- Cleanup old published events (run periodically via cron or pg_cron)
-- DELETE FROM outbox_events WHERE status = 'PUBLISHED' AND published_at < NOW() - INTERVAL '7 days';
