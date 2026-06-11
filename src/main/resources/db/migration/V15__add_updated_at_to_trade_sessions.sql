ALTER TABLE trade_sessions ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ;
UPDATE trade_sessions SET updated_at = started_at WHERE updated_at IS NULL;