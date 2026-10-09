-- Transactional outbox: events are written in the same DB transaction as the
-- order change and relayed to Kafka asynchronously by OutboxRelay.
CREATE TABLE outbox_events (
    id             UUID PRIMARY KEY,
    aggregate_id   UUID         NOT NULL,
    event_type     VARCHAR(50)  NOT NULL,
    payload        TEXT         NOT NULL,
    created_at     TIMESTAMP WITH TIME ZONE NOT NULL,
    published_at   TIMESTAMP WITH TIME ZONE
);

CREATE INDEX idx_outbox_unpublished ON outbox_events (published_at, created_at);

-- Idempotent consumer: every handled event id is recorded so redeliveries are skipped.
CREATE TABLE processed_events (
    event_id      UUID PRIMARY KEY,
    event_type    VARCHAR(50) NOT NULL,
    processed_at  TIMESTAMP WITH TIME ZONE NOT NULL
);
