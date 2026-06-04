-- V9: price_ticks retention index
-- Accelerates the scheduled DELETE in PriceTickCleanupScheduler.
-- The idx_price_ticks_symbol composite index from V4 covers reads.
-- This plain index on tick_time covers the DELETE WHERE tick_time < :cutoff.
CREATE INDEX IF NOT EXISTS idx_price_ticks_cleanup ON price_ticks(tick_time);