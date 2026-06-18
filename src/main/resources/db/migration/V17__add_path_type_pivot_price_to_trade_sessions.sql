ALTER TABLE trade_sessions
    ADD COLUMN IF NOT EXISTS path_type   VARCHAR(20),
    ADD COLUMN IF NOT EXISTS pivot_price NUMERIC(20, 6);
