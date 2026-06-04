-- V2: Create users and refresh_tokens

CREATE TABLE users (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id           UUID NOT NULL REFERENCES tenants(id),
    email               VARCHAR(255) NOT NULL,
    phone_number        VARCHAR(20),
    password_hash       VARCHAR(255) NOT NULL,
    full_name           VARCHAR(100),
    role                VARCHAR(20) NOT NULL DEFAULT 'PLAYER',
    status              VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    kyc_status          VARCHAR(20) DEFAULT 'NONE',
    kyc_document_url    TEXT,
    is_marketer         BOOLEAN NOT NULL DEFAULT FALSE,
    marketer_balance    NUMERIC(15,2) NOT NULL DEFAULT 0,
    created_by_user_id  UUID REFERENCES users(id),
    last_login_at       TIMESTAMPTZ,
    last_login_ip       VARCHAR(45),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_users_tenant_email UNIQUE(tenant_id, email),
    CONSTRAINT chk_users_role CHECK (role IN ('SUPER_ADMIN','ADMIN','SUPPORT','MARKETER','PLAYER'))
);

CREATE TABLE refresh_tokens (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash  VARCHAR(255) NOT NULL UNIQUE,
    expires_at  TIMESTAMPTZ NOT NULL,
    revoked     BOOLEAN DEFAULT FALSE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_users_tenant_id  ON users(tenant_id);
CREATE INDEX idx_users_email      ON users(email);
CREATE INDEX idx_users_phone      ON users(phone_number);
CREATE INDEX idx_users_role       ON users(tenant_id, role);
CREATE INDEX idx_users_marketer   ON users(tenant_id, is_marketer) WHERE is_marketer = TRUE;
CREATE INDEX idx_refresh_tokens_user ON refresh_tokens(user_id);
CREATE INDEX idx_refresh_tokens_hash ON refresh_tokens(token_hash);