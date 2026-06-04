-- Module 9: marketer withdrawal support
ALTER TABLE tenants
    ADD COLUMN IF NOT EXISTS marketer_withdrawal_enabled BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE tenants
    ADD COLUMN IF NOT EXISTS marketer_max_withdrawal NUMERIC(10,2) DEFAULT 5000.00;