-- V1: Create tenants, tenant_api_keys, tenant_audit_log

CREATE TABLE tenants (
    id                        UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name                      VARCHAR(100) NOT NULL,
    custom_domain             VARCHAR(255) UNIQUE,
    status                    VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    commission_rate           NUMERIC(5,4) NOT NULL DEFAULT 0.1500,
    house_balance             NUMERIC(15,2) NOT NULL DEFAULT 0,
    floor_balance             NUMERIC(15,2) NOT NULL DEFAULT 500000,
    platform_mode             VARCHAR(10) NOT NULL DEFAULT 'NORMAL',
    kill_switch_active        BOOLEAN NOT NULL DEFAULT FALSE,
    daraja_consumer_key       TEXT,
    daraja_consumer_secret    TEXT,
    daraja_passkey            TEXT,
    daraja_shortcode          VARCHAR(20),
    daraja_b2c_initiator_name TEXT,
    daraja_b2c_security_cred  TEXT,
    daraja_environment        VARCHAR(10) DEFAULT 'LIVE',
    daraja_c2b_registered     BOOLEAN DEFAULT FALSE,
    min_deposit               NUMERIC(10,2) DEFAULT 10,
    max_deposit               NUMERIC(10,2) DEFAULT 300000,
    min_withdrawal            NUMERIC(10,2) DEFAULT 50,
    max_withdrawal            NUMERIC(10,2) DEFAULT 150000,
    auto_withdrawal_limit     NUMERIC(10,2) DEFAULT 5000,
    demo_balance              NUMERIC(10,2) DEFAULT 10000,
    default_marketer_balance  NUMERIC(10,2) DEFAULT 50000,
    registration_open         BOOLEAN DEFAULT TRUE,
    kyc_required              BOOLEAN DEFAULT FALSE,
    max_concurrent_trades     INT DEFAULT 3,
    brand_name                VARCHAR(100),
    logo_url                  TEXT,
    favicon_url               TEXT,
    primary_color             VARCHAR(7) DEFAULT '#38b6d8',
    accent_color              VARCHAR(7) DEFAULT '#00c896',
    support_email             VARCHAR(255),
    legal_name                VARCHAR(200),
    country_code              VARCHAR(2) DEFAULT 'KE',
    created_at                TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at                TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

INSERT INTO tenants (
    id, name, brand_name, status, commission_rate,
    floor_balance, country_code, created_at, updated_at
) VALUES (
    '00000000-0000-0000-0000-000000000001',
    'LeziFx Master',
    'LeziFx',
    'ACTIVE',
    0.0000,
    1000000,
    'KE',
    NOW(),
    NOW()
);

CREATE TABLE tenant_api_keys (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id   UUID NOT NULL REFERENCES tenants(id),
    api_key     VARCHAR(64) NOT NULL UNIQUE,
    label       VARCHAR(100),
    is_active   BOOLEAN NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    revoked_at  TIMESTAMPTZ
);

INSERT INTO tenant_api_keys (tenant_id, api_key, label, created_at)
VALUES (
    '00000000-0000-0000-0000-000000000001',
    'lzfx_master_00000000000000000000000000000001',
    'Master Production Key',
    NOW()
);

CREATE TABLE tenant_audit_log (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id   UUID NOT NULL REFERENCES tenants(id),
    actor_id    UUID,
    actor_role  VARCHAR(20),
    action      VARCHAR(100) NOT NULL,
    entity_type VARCHAR(50),
    entity_id   UUID,
    old_value   JSONB,
    new_value   JSONB,
    ip_address  VARCHAR(45),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_tenant_api_keys_key    ON tenant_api_keys(api_key);
CREATE INDEX idx_tenant_api_keys_tenant ON tenant_api_keys(tenant_id);
CREATE INDEX idx_tenant_audit_tenant    ON tenant_audit_log(tenant_id, created_at DESC);