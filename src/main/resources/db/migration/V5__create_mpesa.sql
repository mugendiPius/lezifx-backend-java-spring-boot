-- V5: Create deposit_requests, withdrawal_requests, mpesa_callbacks

CREATE TABLE deposit_requests (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id               UUID NOT NULL REFERENCES tenants(id),
    user_id                 UUID NOT NULL REFERENCES users(id),
    amount                  NUMERIC(10,2) NOT NULL,
    phone_number            VARCHAR(20) NOT NULL,
    merchant_request_id     VARCHAR(100) UNIQUE,
    checkout_request_id     VARCHAR(100) UNIQUE,
    status                  VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    mpesa_receipt_number    VARCHAR(50),
    mpesa_transaction_date  TIMESTAMPTZ,
    failure_reason          TEXT,
    is_mock                 BOOLEAN NOT NULL DEFAULT FALSE,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    completed_at            TIMESTAMPTZ
);

CREATE TABLE withdrawal_requests (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id                   UUID NOT NULL REFERENCES tenants(id),
    user_id                     UUID NOT NULL REFERENCES users(id),
    amount                      NUMERIC(10,2) NOT NULL,
    phone_number                VARCHAR(20) NOT NULL,
    status                      VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    approval_note               TEXT,
    approved_by                 UUID REFERENCES users(id),
    approved_at                 TIMESTAMPTZ,
    conversation_id             VARCHAR(100),
    originator_conversation_id  VARCHAR(100),
    mpesa_receipt_number        VARCHAR(50),
    failure_reason              TEXT,
    created_at                  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    completed_at                TIMESTAMPTZ
);

CREATE TABLE mpesa_callbacks (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id        UUID REFERENCES tenants(id),
    callback_type    VARCHAR(20) NOT NULL,
    raw_payload      JSONB NOT NULL,
    processed        BOOLEAN DEFAULT FALSE,
    reference_id     UUID,
    processing_error TEXT,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_callback_type CHECK (callback_type IN ('C2B_CONFIRM','B2C_RESULT','B2C_TIMEOUT'))
);

CREATE INDEX idx_deposits_tenant    ON deposit_requests(tenant_id, created_at DESC);
CREATE INDEX idx_deposits_user      ON deposit_requests(user_id, created_at DESC);
CREATE INDEX idx_deposits_checkout  ON deposit_requests(checkout_request_id);
CREATE INDEX idx_deposits_merchant  ON deposit_requests(merchant_request_id);
CREATE INDEX idx_deposits_status    ON deposit_requests(tenant_id, status);
CREATE INDEX idx_withdrawals_tenant ON withdrawal_requests(tenant_id, created_at DESC);
CREATE INDEX idx_withdrawals_user   ON withdrawal_requests(user_id);
CREATE INDEX idx_withdrawals_status ON withdrawal_requests(tenant_id, status);
CREATE INDEX idx_mpesa_callbacks_processed ON mpesa_callbacks(processed, created_at) WHERE processed = FALSE;