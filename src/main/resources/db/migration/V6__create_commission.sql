-- V6: Create commission_statements and commission_payments

CREATE TABLE commission_statements (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id           UUID NOT NULL REFERENCES tenants(id),
    period_start        DATE NOT NULL,
    period_end          DATE NOT NULL,
    total_trades        INT NOT NULL DEFAULT 0,
    total_staked        NUMERIC(15,2) NOT NULL DEFAULT 0,
    total_paid_out      NUMERIC(15,2) NOT NULL DEFAULT 0,
    gross_profit        NUMERIC(15,2) NOT NULL DEFAULT 0,
    platform_commission NUMERIC(15,2) NOT NULL DEFAULT 0,
    tenant_net          NUMERIC(15,2) NOT NULL DEFAULT 0,
    status              VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    generated_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    due_at              TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_commission_tenant_period UNIQUE(tenant_id, period_start)
);

CREATE TABLE commission_payments (
    id                   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    statement_id         UUID NOT NULL REFERENCES commission_statements(id),
    tenant_id            UUID NOT NULL REFERENCES tenants(id),
    amount               NUMERIC(15,2) NOT NULL,
    payment_method       VARCHAR(20),
    mpesa_transaction_id VARCHAR(100),
    notes                TEXT,
    recorded_by          UUID REFERENCES users(id),
    paid_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_payment_method CHECK (payment_method IN ('MPESA_B2B','MANUAL','BANK'))
);

CREATE INDEX idx_commission_tenant ON commission_statements(tenant_id);
CREATE INDEX idx_commission_status ON commission_statements(status);
CREATE INDEX idx_commission_due    ON commission_statements(due_at) WHERE status = 'PENDING';