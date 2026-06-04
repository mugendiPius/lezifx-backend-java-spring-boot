-- V7: Create social_feed_events

CREATE TABLE social_feed_events (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id               UUID NOT NULL REFERENCES tenants(id),
    event_type              VARCHAR(20) NOT NULL,
    display_name            VARCHAR(100) NOT NULL,
    action                  VARCHAR(20) NOT NULL,
    amount                  NUMERIC(15,2) NOT NULL,
    pair_symbol             VARCHAR(20),
    is_simulated            BOOLEAN NOT NULL DEFAULT FALSE,
    source_trade_session_id UUID REFERENCES trade_sessions(id),
    created_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_event_type CHECK (event_type IN ('REAL_WIN','REAL_STAKE','MARKETER_WIN','MARKETER_STAKE','SIMULATED')),
    CONSTRAINT chk_action CHECK (action IN ('won','staked','cashed'))
);

CREATE INDEX idx_social_feed_tenant ON social_feed_events(tenant_id, created_at DESC);