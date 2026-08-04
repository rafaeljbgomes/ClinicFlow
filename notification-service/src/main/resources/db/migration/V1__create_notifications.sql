CREATE TABLE notifications (
    id UUID PRIMARY KEY,
    event_id UUID NOT NULL UNIQUE,
    event_type VARCHAR(80) NOT NULL,
    type VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL,
    recipient VARCHAR(320),
    subject VARCHAR(160) NOT NULL,
    message VARCHAR(1000) NOT NULL,
    failure_reason VARCHAR(500),
    created_at TIMESTAMPTZ NOT NULL,
    sent_at TIMESTAMPTZ
);

CREATE INDEX idx_notifications_event_type ON notifications (event_type);
CREATE INDEX idx_notifications_status ON notifications (status);
