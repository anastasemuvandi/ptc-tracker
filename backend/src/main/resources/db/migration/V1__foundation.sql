-- =====================================================================
-- PTC Tracker — foundation: roles, tenancy helpers, institutions,
-- configuration with effective dates, holidays, counters, audit trail.
--
-- Conventions (apply to every table in every migration):
--   * id uuid PRIMARY KEY DEFAULT gen_random_uuid(); the application
--     supplies UUIDv7 values, the default is a safety net only.
--   * Every foreign key is uuid, REFERENCES ... ON DELETE RESTRICT, and
--     indexed.
--   * Tenant tables carry institution_id and are protected by RLS keyed
--     on the session variable app.institution_id (see V9).
--   * Money is NUMERIC(20,2) with a currency char(3) alongside.
--   * Timestamps are timestamptz (UTC); calendar dates are date.
-- =====================================================================

-- Login roles are created by the deployment (deploy/db/init) with
-- passwords; create them NOLOGIN here so migrations are self-sufficient.
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'ptc_app') THEN
        CREATE ROLE ptc_app NOLOGIN;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'ptc_auth') THEN
        CREATE ROLE ptc_auth NOLOGIN;
    END IF;
END
$$;

REVOKE ALL ON SCHEMA public FROM PUBLIC;
GRANT USAGE ON SCHEMA public TO ptc_app, ptc_auth;

-- ---------------------------------------------------------------------
-- Session-variable helpers used by every RLS policy.
-- ---------------------------------------------------------------------
CREATE FUNCTION app_current_institution() RETURNS uuid
    LANGUAGE sql STABLE AS
$$ SELECT NULLIF(current_setting('app.institution_id', true), '')::uuid $$;

CREATE FUNCTION app_current_user() RETURNS uuid
    LANGUAGE sql STABLE AS
$$ SELECT NULLIF(current_setting('app.user_id', true), '')::uuid $$;

CREATE FUNCTION app_is_platform_admin() RETURNS boolean
    LANGUAGE sql STABLE AS
$$ SELECT coalesce(current_setting('app.platform_admin', true), '') = 'on' $$;

-- ---------------------------------------------------------------------
-- Institution (the tenant)
-- ---------------------------------------------------------------------
CREATE TABLE institution (
    id                      uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    name                    text        NOT NULL,
    abbreviation            text        NOT NULL,
    institution_type        text        NOT NULL,
    supervising_authority   text,
    minutes_letterhead      text,
    minutes_font_family     text        NOT NULL DEFAULT 'Calibri',
    minutes_font_size       integer     NOT NULL DEFAULT 11,
    minutes_template_docx   bytea,
    holiday_override        boolean     NOT NULL DEFAULT false,
    default_currency        char(3)     NOT NULL DEFAULT 'RWF',
    status                  text        NOT NULL DEFAULT 'ACTIVE'
                            CHECK (status IN ('ACTIVE', 'SUSPENDED')),
    created_at              timestamptz NOT NULL DEFAULT now(),
    version                 bigint      NOT NULL DEFAULT 0,
    CONSTRAINT uq_institution_abbreviation UNIQUE (abbreviation)
);

-- ---------------------------------------------------------------------
-- Users. institution_id is NULL only for platform administrators.
-- ---------------------------------------------------------------------
CREATE TABLE app_user (
    id                      uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    institution_id          uuid REFERENCES institution (id) ON DELETE RESTRICT,
    email                   text        NOT NULL,
    full_name               text        NOT NULL,
    job_title               text,
    phone                   text,
    -- Office held in the institution; used for committee exclusion rules
    -- (e.g. HEAD_OF_INSTITUTION, HEAD_OF_FINANCE, INTERNAL_AUDITOR ...).
    office_holder_code      text,
    is_procurement_officer  boolean     NOT NULL DEFAULT false,
    locale                  text        NOT NULL DEFAULT 'en'
                            CHECK (locale IN ('en', 'rw', 'fr')),
    status                  text        NOT NULL DEFAULT 'INVITED'
                            CHECK (status IN ('INVITED', 'ACTIVE', 'LOCKED', 'DISABLED')),
    token_version           integer     NOT NULL DEFAULT 1,
    created_at              timestamptz NOT NULL DEFAULT now(),
    version                 bigint      NOT NULL DEFAULT 0
);
CREATE UNIQUE INDEX uq_app_user_email ON app_user (lower(email));
CREATE INDEX ix_app_user_institution ON app_user (institution_id);

-- Institution-level roles (committee roles live on committee_membership).
CREATE TABLE user_role (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         uuid NOT NULL REFERENCES app_user (id) ON DELETE RESTRICT,
    institution_id  uuid REFERENCES institution (id) ON DELETE RESTRICT,
    role            text NOT NULL CHECK (role IN
                    ('PLATFORM_ADMIN', 'INSTITUTION_ADMIN', 'CBM', 'READ_ONLY')),
    granted_at      timestamptz NOT NULL DEFAULT now(),
    granted_by      uuid REFERENCES app_user (id) ON DELETE RESTRICT,
    CONSTRAINT uq_user_role UNIQUE (user_id, role),
    CONSTRAINT ck_platform_admin_no_tenant CHECK
        ((role = 'PLATFORM_ADMIN') = (institution_id IS NULL))
);
CREATE INDEX ix_user_role_user ON user_role (user_id);
CREATE INDEX ix_user_role_institution ON user_role (institution_id);
CREATE INDEX ix_user_role_granted_by ON user_role (granted_by);

-- ---------------------------------------------------------------------
-- Configuration with effective dates and history.
-- institution_id NULL = platform default. Rows are never updated: a new
-- row with a later effective_from supersedes the previous one.
-- ---------------------------------------------------------------------
CREATE TABLE config_rule (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    institution_id  uuid REFERENCES institution (id) ON DELETE RESTRICT,
    rule_key        text        NOT NULL,
    rule_value      jsonb       NOT NULL,
    effective_from  timestamptz NOT NULL,
    reason          text,
    legal_reference text,
    created_by      uuid REFERENCES app_user (id) ON DELETE RESTRICT,
    created_at      timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX ix_config_rule_lookup ON config_rule (rule_key, institution_id, effective_from DESC);
CREATE INDEX ix_config_rule_institution ON config_rule (institution_id);
CREATE INDEX ix_config_rule_created_by ON config_rule (created_by);

-- ---------------------------------------------------------------------
-- Public holiday calendar. Platform rows (institution_id NULL) form the
-- national calendar; institution rows ADD or REMOVE dates when the
-- institution has holiday_override = true.
-- ---------------------------------------------------------------------
CREATE TABLE holiday (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    institution_id  uuid REFERENCES institution (id) ON DELETE RESTRICT,
    holiday_date    date NOT NULL,
    name            text NOT NULL,
    kind            text NOT NULL DEFAULT 'ADD' CHECK (kind IN ('ADD', 'REMOVE')),
    created_at      timestamptz NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX uq_holiday ON holiday (coalesce(institution_id, '00000000-0000-0000-0000-000000000000'::uuid), holiday_date);
CREATE INDEX ix_holiday_institution ON holiday (institution_id);

-- ---------------------------------------------------------------------
-- Per-institution sequential counters for human-facing numbers
-- (file numbers, session numbers). Never derived from UUIDs.
-- ---------------------------------------------------------------------
CREATE TABLE institution_counter (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    institution_id  uuid    NOT NULL REFERENCES institution (id) ON DELETE RESTRICT,
    counter_key     text    NOT NULL,
    last_value      integer NOT NULL DEFAULT 0,
    CONSTRAINT uq_institution_counter UNIQUE (institution_id, counter_key)
);

-- ---------------------------------------------------------------------
-- Append-only audit trail.
-- ---------------------------------------------------------------------
CREATE TABLE audit_event (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    institution_id  uuid REFERENCES institution (id) ON DELETE RESTRICT,
    occurred_at     timestamptz NOT NULL DEFAULT now(),
    actor_id        uuid REFERENCES app_user (id) ON DELETE RESTRICT,
    action          text        NOT NULL,
    entity_type     text        NOT NULL,
    entity_id       uuid,
    previous_value  jsonb,
    new_value       jsonb,
    ip_address      text,
    user_agent      text,
    request_id      text
);
CREATE INDEX ix_audit_event_institution_time ON audit_event (institution_id, occurred_at DESC);
CREATE INDEX ix_audit_event_entity ON audit_event (entity_id);
CREATE INDEX ix_audit_event_actor ON audit_event (actor_id);

CREATE FUNCTION audit_event_immutable() RETURNS trigger
    LANGUAGE plpgsql AS
$$
BEGIN
    RAISE EXCEPTION 'audit_event is append-only: % is not permitted', TG_OP
        USING ERRCODE = 'insufficient_privilege';
END
$$;

CREATE TRIGGER trg_audit_event_no_update
    BEFORE UPDATE OR DELETE ON audit_event
    FOR EACH ROW EXECUTE FUNCTION audit_event_immutable();
CREATE TRIGGER trg_audit_event_no_truncate
    BEFORE TRUNCATE ON audit_event
    FOR EACH STATEMENT EXECUTE FUNCTION audit_event_immutable();

-- ---------------------------------------------------------------------
-- Idempotency keys: client-generated UUIDs on mutating requests.
-- ---------------------------------------------------------------------
CREATE TABLE idempotency_record (
    id               uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    institution_id   uuid REFERENCES institution (id) ON DELETE RESTRICT,
    user_id          uuid NOT NULL REFERENCES app_user (id) ON DELETE RESTRICT,
    idempotency_key  uuid NOT NULL,
    request_method   text NOT NULL,
    request_path     text NOT NULL,
    request_hash     text NOT NULL,
    response_status  integer,
    response_body    text,
    created_at       timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uq_idempotency UNIQUE (user_id, idempotency_key)
);
CREATE INDEX ix_idempotency_institution ON idempotency_record (institution_id);
