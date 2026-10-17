CREATE TABLE outbox_events (
    id UUID PRIMARY KEY,
    event_id UUID NOT NULL UNIQUE,
    event_type VARCHAR(100) NOT NULL,
    routing_key VARCHAR(160) NOT NULL,
    payload TEXT NOT NULL,
    correlation_id VARCHAR(128),
    status VARCHAR(16) NOT NULL,
    retry_count INTEGER NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    published_at TIMESTAMPTZ,
    last_error VARCHAR(1000),
    CONSTRAINT chk_outbox_status CHECK (status IN ('PENDING', 'PUBLISHED', 'FAILED')),
    CONSTRAINT chk_outbox_retry_count CHECK (retry_count >= 0)
);

CREATE INDEX idx_outbox_dispatch
    ON outbox_events (status, next_attempt_at, created_at);

CREATE INDEX idx_outbox_published_at
    ON outbox_events (published_at)
    WHERE status = 'PUBLISHED';
