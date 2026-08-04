ALTER TABLE notifications
    ADD COLUMN psychologist_id UUID;

CREATE INDEX idx_notifications_psychologist_created_at
    ON notifications (psychologist_id, created_at DESC);
