-- Module 9: track whether a withdrawal is from a marketer balance
ALTER TABLE withdrawal_requests
    ADD COLUMN IF NOT EXISTS is_marketer_withdrawal BOOLEAN NOT NULL DEFAULT FALSE;