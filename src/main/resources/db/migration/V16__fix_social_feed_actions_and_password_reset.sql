-- V16: Fix social_feed_events action constraint to include 'lost' and 'deposited'
--      and create password_reset_tokens table for OTP-based password reset

-- Drop old 3-value constraint and replace with the full 5-value set
ALTER TABLE social_feed_events DROP CONSTRAINT IF EXISTS chk_action;
ALTER TABLE social_feed_events DROP CONSTRAINT IF EXISTS social_feed_events_action_check;
ALTER TABLE social_feed_events
    ADD CONSTRAINT chk_action CHECK (action IN ('won','lost','staked','cashed','deposited'));

-- Password reset OTP tokens (tenant-scoped, expires in 10 minutes)
CREATE TABLE password_reset_tokens (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id   UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    email       VARCHAR(255) NOT NULL,
    otp_hash    VARCHAR(64) NOT NULL,
    expires_at  TIMESTAMPTZ NOT NULL,
    used        BOOLEAN NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_prt_lookup ON password_reset_tokens(tenant_id, email, used, expires_at);
