-- Module 6: M-Pesa Daraja — performance indexes
-- Speed up withdrawal lookup by conversation ID
CREATE INDEX IF NOT EXISTS idx_withdrawals_conversation
    ON withdrawal_requests(conversation_id)
    WHERE conversation_id IS NOT NULL;

-- Speed up deposit lookup by status (pending STK callbacks)
CREATE INDEX IF NOT EXISTS idx_deposits_pending
    ON deposit_requests(status, created_at)
    WHERE status = 'PENDING';