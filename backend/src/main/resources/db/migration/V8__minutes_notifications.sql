-- =====================================================================
-- Minutes, acknowledgements, notifications, push subscriptions.
-- =====================================================================

CREATE TABLE minutes (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    institution_id  uuid        NOT NULL REFERENCES institution (id) ON DELETE RESTRICT,
    session_id      uuid        NOT NULL REFERENCES committee_session (id) ON DELETE RESTRICT,
    status          text        NOT NULL DEFAULT 'DRAFT'
                    CHECK (status IN ('DRAFT', 'IN_REVIEW', 'FINAL')),
    -- Generated structure (sections per agenda item); the docx is rendered
    -- from this, so a final record is stable even if files change later.
    content         jsonb       NOT NULL,
    secretary_notes text,
    generated_at    timestamptz NOT NULL DEFAULT now(),
    generated_by    uuid        REFERENCES app_user (id) ON DELETE RESTRICT,
    finalised_at    timestamptz,
    finalised_by    uuid        REFERENCES app_user (id) ON DELETE RESTRICT,
    version         bigint      NOT NULL DEFAULT 0,
    CONSTRAINT uq_minutes_session UNIQUE (session_id)
);
CREATE INDEX ix_minutes_institution ON minutes (institution_id);
CREATE INDEX ix_minutes_generated_by ON minutes (generated_by);
CREATE INDEX ix_minutes_finalised_by ON minutes (finalised_by);

CREATE TABLE minutes_acknowledgement (
    id               uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    institution_id   uuid        NOT NULL REFERENCES institution (id) ON DELETE RESTRICT,
    minutes_id       uuid        NOT NULL REFERENCES minutes (id) ON DELETE RESTRICT,
    user_id          uuid        NOT NULL REFERENCES app_user (id) ON DELETE RESTRICT,
    -- Version of the minutes acknowledged; a regeneration invalidates it.
    minutes_version  bigint      NOT NULL,
    acknowledged_at  timestamptz NOT NULL DEFAULT now(),
    comment          text,
    CONSTRAINT uq_minutes_ack UNIQUE (minutes_id, user_id, minutes_version)
);
CREATE INDEX ix_minutes_ack_institution ON minutes_acknowledgement (institution_id);
CREATE INDEX ix_minutes_ack_user ON minutes_acknowledgement (user_id);

CREATE TABLE notification (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    institution_id  uuid        NOT NULL REFERENCES institution (id) ON DELETE RESTRICT,
    user_id         uuid        NOT NULL REFERENCES app_user (id) ON DELETE RESTRICT,
    kind            text        NOT NULL,
    title           text        NOT NULL,
    body            text        NOT NULL,
    -- In-app route of the landing page that answers one question.
    link            text        NOT NULL,
    entity_type     text,
    entity_id       uuid,
    -- Prevents a reminder being sent twice for the same occurrence.
    dedupe_key      text        NOT NULL,
    created_at      timestamptz NOT NULL DEFAULT now(),
    read_at         timestamptz,
    email_sent_at   timestamptz,
    email_error     text,
    push_sent_at    timestamptz,
    CONSTRAINT uq_notification_dedupe UNIQUE (user_id, dedupe_key)
);
CREATE INDEX ix_notification_institution ON notification (institution_id);
CREATE INDEX ix_notification_user ON notification (user_id, read_at, created_at DESC);
CREATE INDEX ix_notification_entity ON notification (entity_id);

CREATE TABLE push_subscription (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    institution_id  uuid        NOT NULL REFERENCES institution (id) ON DELETE RESTRICT,
    user_id         uuid        NOT NULL REFERENCES app_user (id) ON DELETE RESTRICT,
    endpoint        text        NOT NULL,
    p256dh          text        NOT NULL,
    auth_secret     text        NOT NULL,
    user_agent      text,
    created_at      timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uq_push_endpoint UNIQUE (endpoint)
);
CREATE INDEX ix_push_institution ON push_subscription (institution_id);
CREATE INDEX ix_push_user ON push_subscription (user_id);
