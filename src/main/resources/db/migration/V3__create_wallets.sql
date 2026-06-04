-- V3: Create wallets and wallet_transactions

CREATE TABLE wallets (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id      UUID NOT NULL UNIQUE REFERENCES users(id),
    tenant_id    UUID NOT NULL REFERENCES tenants(id),
    live_balance NUMERIC(15,2) NOT NULL DEFAULT 0,
    demo_balance NUMERIC(15,2) NOT NULL DEFAULT 10000,
    version      BIGINT NOT NULL DEFAULT 0,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT live_balance_non_negative CHECK (live_balance >= 0),
    CONSTRAINT demo_balance_non_negative CHECK (demo_balance >= 0)
);

CREATE TABLE wallet_transactions (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    wallet_id               UUID NOT NULL REFERENCES wallets(id),
    tenant_id               UUID NOT NULL REFERENCES tenants(id),
    type                    VARCHAR(30) NOT NULL,
    amount                  NUMERIC(15,2) NOT NULL,
    balance_before          NUMERIC(15,2) NOT NULL,
    balance_after           NUMERIC(15,2) NOT NULL,
    is_demo                 BOOLEAN NOT NULL DEFAULT FALSE,
    is_marketer_transaction BOOLEAN NOT NULL DEFAULT FALSE,
    reference_id            UUID,
    reference_type          VARCHAR(30),
    description             TEXT,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_wallet_tx_type CHECK (type IN (
        'DEPOSIT','WITHDRAWAL','TRADE_STAKE','TRADE_WIN','TRADE_LOSS',
        'TRADE_REFUND','ADMIN_ADJUSTMENT','DEMO_REFILL',
        'MOCK_DEPOSIT','MOCK_WITHDRAWAL',
        'MARKETER_TRADE_WIN','MARKETER_TRADE_LOSS'
    ))
);

CREATE INDEX idx_wallets_user       ON wallets(user_id);
CREATE INDEX idx_wallets_tenant     ON wallets(tenant_id);
CREATE INDEX idx_wallet_tx_wallet   ON wallet_transactions(wallet_id);
CREATE INDEX idx_wallet_tx_tenant   ON wallet_transactions(tenant_id);
CREATE INDEX idx_wallet_tx_created  ON wallet_transactions(created_at DESC);
CREATE INDEX idx_wallet_tx_type     ON wallet_transactions(tenant_id, type);
CREATE INDEX idx_wallet_tx_reference ON wallet_transactions(reference_id) WHERE reference_id IS NOT NULL;