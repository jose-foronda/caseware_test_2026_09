CREATE USER local WITH PASSWORD 'local' CREATEDB;
CREATE DATABASE decision_engine_db
    WITH
    OWNER = local
    ENCODING = 'UTF8'
    LC_COLLATE = 'en_US.utf8'
    LC_CTYPE = 'en_US.utf8'
    TABLESPACE = pg_default
    CONNECTION LIMIT = -1;

GRANT ALL PRIVILEGES ON DATABASE decision_engine_db TO local;

\c decision_engine_db;

CREATE TABLE dmn_models (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    version VARCHAR(50) NOT NULL,
    content TEXT NOT NULL,
    status VARCHAR(20) DEFAULT 'ACTIVE',
    created_at TIMESTAMP DEFAULT NOW(),
    created_by VARCHAR(100),
    CONSTRAINT unique_name_version UNIQUE(name, version)
);

CREATE INDEX idx_active_models ON dmn_models(name, status) WHERE status = 'ACTIVE';

GRANT ALL PRIVILEGES ON TABLE dmn_models TO local;
GRANT USAGE, SELECT ON SEQUENCE dmn_models_id_seq TO local;

-- ────────────────────────────────────────────────────────────────────────────
-- em — Engagement Management schema (see architecture/erd.md)
-- ────────────────────────────────────────────────────────────────────────────

CREATE SCHEMA IF NOT EXISTS em;

-- Engagement metadata + blob pointer. current_version_id is the authoritative version.
CREATE TABLE em.em_engagement (
    engagement_id      UUID PRIMARY KEY,
    client_id          UUID NOT NULL,
    tenant_id          UUID NOT NULL,
    template_id        UUID NOT NULL,
    current_version_id UUID NOT NULL,
    fiscal_year        INTEGER NOT NULL,
    location_key       VARCHAR(500) NOT NULL,
    status             VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_by         VARCHAR(100),
    created_at         TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_by         VARCHAR(100),
    updated_at         TIMESTAMPTZ
);

-- Projection rebuilt from events. Never the source of truth.
-- update_status is derived at read time: UPDATES_REVIEWED if
-- last_decided_version_id = latest_version_id, else PENDING_UPDATES.
CREATE TABLE em.em_engagement_read_model (
    engagement_id          UUID PRIMARY KEY,
    tenant_id              UUID NOT NULL,
    template_id            UUID NOT NULL,
    current_version_id     UUID NOT NULL,
    latest_version_id      UUID NOT NULL,
    last_decided_version_id UUID,
    fiscal_year            INTEGER NOT NULL
);

CREATE INDEX idx_em_read_model_tenant ON em.em_engagement_read_model (tenant_id);

-- Append-only decision trail.
CREATE TABLE em.em_update_decision (
    decision_id       UUID PRIMARY KEY,
    engagement_id     UUID NOT NULL,
    template_id       UUID NOT NULL,
    from_version_id   UUID NOT NULL,
    target_version_id UUID NOT NULL,
    decision          VARCHAR(20) NOT NULL,
    summary_id        UUID,
    decided_by        VARCHAR(100) NOT NULL,
    reason            TEXT,
    decided_at        TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_em_update_decision_engagement ON em.em_update_decision (engagement_id);

GRANT ALL PRIVILEGES ON SCHEMA em, public TO local;
GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA em TO local;

-- ────────────────────────────────────────────────────────────────────────────
-- Demo seed data (UC-5 / UC-7)
-- Tenants: t-1 (pending update), t-2 (up to date)
-- ────────────────────────────────────────────────────────────────────────────

INSERT INTO em.em_engagement
    (engagement_id, client_id, tenant_id, template_id, current_version_id, fiscal_year, location_key, status)
VALUES
    ('11111111-1111-1111-1111-111111111111', '22222222-2222-2222-2222-222222222221',
     '99999999-9999-9999-9999-999999999991', 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', '00000000-0000-0000-0000-000000000001', 2026, 'engagements/11111111.zip', 'ACTIVE'),
    ('11111111-1111-1111-1111-111111111112', '22222222-2222-2222-2222-222222222222',
     '99999999-9999-9999-9999-999999999992', 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', '00000000-0000-0000-0000-000000000002', 2026, 'engagements/11111111-2.zip', 'ACTIVE');

INSERT INTO em.em_engagement_read_model
    (engagement_id, tenant_id, template_id, current_version_id, latest_version_id, last_decided_version_id, fiscal_year)
VALUES
    ('11111111-1111-1111-1111-111111111111', '99999999-9999-9999-9999-999999999991',
     'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', '00000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000002', '00000000-0000-0000-0000-000000000001', 2026),
    ('11111111-1111-1111-1111-111111111112', '99999999-9999-9999-9999-999999999992',
     'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', '00000000-0000-0000-0000-000000000002', '00000000-0000-0000-0000-000000000002', '00000000-0000-0000-0000-000000000002', 2026);

INSERT INTO em.em_update_decision
    (decision_id, engagement_id, template_id, from_version_id, target_version_id, decision, decided_by, decided_at)
VALUES
    ('33333333-3333-3333-3333-333333333333', '11111111-1111-1111-1111-111111111112',
     'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', '00000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000002', 'APPLIED', 'demo-user', NOW());