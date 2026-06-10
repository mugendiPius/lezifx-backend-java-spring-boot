-- V14: Replace custom_domain (single) with allowed_origins (array).
-- This allows each tenant to register multiple domains and deployment URLs
-- so that PublicConfigController can resolve any of them to the correct tenant.

ALTER TABLE tenants DROP COLUMN IF EXISTS custom_domain;
ALTER TABLE tenants ADD COLUMN IF NOT EXISTS allowed_origins TEXT[] NOT NULL DEFAULT '{}';

-- Seed master tenant with all known origins
UPDATE tenants
SET allowed_origins = ARRAY[
    'lezifx.com',
    'www.lezifx.com',
    'jetfx-gametrade.onrender.com',
    'localhost:5173',
    'localhost:5174',
    'localhost:8080'
]
WHERE id = '00000000-0000-0000-0000-000000000001';

-- GIN index for fast array containment queries (@> operator)
CREATE INDEX IF NOT EXISTS idx_tenants_allowed_origins
    ON tenants USING GIN(allowed_origins);