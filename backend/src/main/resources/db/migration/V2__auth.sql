-- =====================================================================
-- Auth module tables. Only the ptc_auth role may touch these (see V9);
-- the rest of the application never reads credentials or tokens.
-- =====================================================================

CREATE TABLE user_credential (
    id                   uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id              uuid        NOT NULL REFERENCES app_user (id) ON DELETE RESTRICT,
    password_hash        text        NOT NULL,
    password_changed_at  timestamptz NOT NULL DEFAULT now(),
    failed_attempts      integer     NOT NULL DEFAULT 0,
    last_failed_at       timestamptz,
    locked_until         timestamptz,
    CONSTRAINT uq_user_credential_user UNIQUE (user_id)
);

CREATE TABLE mfa_totp (
    id                uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id           uuid        NOT NULL REFERENCES app_user (id) ON DELETE RESTRICT,
    -- AES-GCM encrypted base32 secret (key held outside the database).
    secret_encrypted  text        NOT NULL,
    confirmed_at      timestamptz,
    last_used_step    bigint,
    created_at        timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uq_mfa_totp_user UNIQUE (user_id)
);

CREATE TABLE mfa_recovery_code (
    id          uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     uuid        NOT NULL REFERENCES app_user (id) ON DELETE RESTRICT,
    code_hash   text        NOT NULL,
    used_at     timestamptz,
    created_at  timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX ix_mfa_recovery_code_user ON mfa_recovery_code (user_id);

-- Refresh tokens: opaque, stored as SHA-256 hash. family_id groups a
-- rotation chain; re-use of a consumed token revokes the whole family.
-- family_id is also the access token's sid claim and scopes step-up.
CREATE TABLE refresh_token (
    id            uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id       uuid        NOT NULL REFERENCES app_user (id) ON DELETE RESTRICT,
    family_id     uuid        NOT NULL,
    token_hash    text        NOT NULL,
    issued_at     timestamptz NOT NULL DEFAULT now(),
    expires_at    timestamptz NOT NULL,
    consumed_at   timestamptz,
    revoked_at    timestamptz,
    revoke_reason text,
    replaced_by   uuid REFERENCES refresh_token (id) ON DELETE RESTRICT,
    mfa_verified  boolean     NOT NULL DEFAULT false,
    user_agent    text,
    CONSTRAINT uq_refresh_token_hash UNIQUE (token_hash)
);
CREATE INDEX ix_refresh_token_user ON refresh_token (user_id);
CREATE INDEX ix_refresh_token_family ON refresh_token (family_id);
CREATE INDEX ix_refresh_token_replaced_by ON refresh_token (replaced_by);

-- Single-use, time-limited tokens for invitations and password resets.
CREATE TABLE auth_one_time_token (
    id          uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     uuid        NOT NULL REFERENCES app_user (id) ON DELETE RESTRICT,
    purpose     text        NOT NULL CHECK (purpose IN ('INVITE', 'PASSWORD_RESET')),
    token_hash  text        NOT NULL,
    expires_at  timestamptz NOT NULL,
    used_at     timestamptz,
    created_by  uuid REFERENCES app_user (id) ON DELETE RESTRICT,
    created_at  timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uq_one_time_token_hash UNIQUE (token_hash)
);
CREATE INDEX ix_one_time_token_user ON auth_one_time_token (user_id);
CREATE INDEX ix_one_time_token_created_by ON auth_one_time_token (created_by);

-- Step-up authentication grants, scoped to a login session (sid).
CREATE TABLE auth_step_up (
    id           uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id      uuid        NOT NULL REFERENCES app_user (id) ON DELETE RESTRICT,
    session_id   uuid        NOT NULL,
    method       text        NOT NULL CHECK (method IN ('TOTP', 'PASSWORD', 'RECOVERY_CODE')),
    verified_at  timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX ix_auth_step_up_user_session ON auth_step_up (user_id, session_id, verified_at DESC);
