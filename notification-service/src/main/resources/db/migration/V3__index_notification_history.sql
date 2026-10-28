-- The administrator's event history pages through every notification, newest first (ADR-029).
CREATE INDEX idx_notifications_created_at_id
    ON notifications (created_at DESC, id DESC);
