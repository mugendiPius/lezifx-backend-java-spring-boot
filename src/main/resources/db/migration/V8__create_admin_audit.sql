-- V8: Create admin_audit_log

CREATE TABLE admin_audit_log (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id   UUID NOT NULL REFERENCES tenants(id),
    actor_id    UUID REFERENCES users(id),
    actor_role  VARCHAR(20),
    action      VARCHAR(100) NOT NULL,
    entity_type VARCHAR(50),
    entity_id   UUID,
    old_value   JSONB,
    new_value   JSONB,
    ip_address  VARCHAR(45),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_audit_tenant ON admin_audit_log(tenant_id, created_at DESC);
CREATE INDEX idx_audit_actor  ON admin_audit_log(actor_id);
CREATE INDEX idx_audit_entity ON admin_audit_log(entity_type, entity_id) WHERE entity_id IS NOT NULL;