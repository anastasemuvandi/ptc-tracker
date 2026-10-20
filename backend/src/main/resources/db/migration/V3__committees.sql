-- =====================================================================
-- Committees, memberships, replacements and tracked tasks.
-- =====================================================================

CREATE TABLE committee (
    id                      uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    institution_id          uuid        NOT NULL REFERENCES institution (id) ON DELETE RESTRICT,
    name                    text        NOT NULL,
    code                    text        NOT NULL,
    -- Constituted size; quorum is computed from this, per committee.
    size                    integer     NOT NULL CHECK (size > 0),
    -- Required where the institution has more than one committee.
    regulator_auth_ref      text,
    regulator_auth_date     date,
    status                  text        NOT NULL DEFAULT 'ACTIVE'
                            CHECK (status IN ('ACTIVE', 'DISSOLVED')),
    created_by              uuid REFERENCES app_user (id) ON DELETE RESTRICT,
    created_at              timestamptz NOT NULL DEFAULT now(),
    version                 bigint      NOT NULL DEFAULT 0,
    CONSTRAINT uq_committee_code UNIQUE (institution_id, code)
);
CREATE INDEX ix_committee_institution ON committee (institution_id);
CREATE INDEX ix_committee_created_by ON committee (created_by);

CREATE TABLE committee_membership (
    id                  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    institution_id      uuid        NOT NULL REFERENCES institution (id) ON DELETE RESTRICT,
    committee_id        uuid        NOT NULL REFERENCES committee (id) ON DELETE RESTRICT,
    user_id             uuid        NOT NULL REFERENCES app_user (id) ON DELETE RESTRICT,
    role                text        NOT NULL CHECK (role IN ('CHAIRPERSON', 'VICE_CHAIRPERSON', 'SECRETARY', 'MEMBER')),
    term_start          date        NOT NULL,
    term_end            date        NOT NULL,
    -- 1 = first term; 2 = first consecutive renewal, ...
    consecutive_term    integer     NOT NULL DEFAULT 1 CHECK (consecutive_term >= 1),
    status              text        NOT NULL DEFAULT 'ACTIVE'
                        CHECK (status IN ('ACTIVE', 'ENDED', 'REPLACED')),
    appointment_ref     text,
    appointed_by        uuid        REFERENCES app_user (id) ON DELETE RESTRICT,
    appointed_at        timestamptz NOT NULL DEFAULT now(),
    ended_at            timestamptz,
    end_reason          text,
    replacement_id      uuid,
    version             bigint      NOT NULL DEFAULT 0,
    CONSTRAINT ck_membership_term CHECK (term_end > term_start)
);
CREATE INDEX ix_membership_institution ON committee_membership (institution_id);
CREATE INDEX ix_membership_committee ON committee_membership (committee_id);
CREATE INDEX ix_membership_user ON committee_membership (user_id);
CREATE INDEX ix_membership_appointed_by ON committee_membership (appointed_by);
-- A person holds at most one active seat per committee.
CREATE UNIQUE INDEX uq_membership_active_seat
    ON committee_membership (committee_id, user_id) WHERE status = 'ACTIVE';
-- Officer roles are unique among active seats.
CREATE UNIQUE INDEX uq_membership_active_office
    ON committee_membership (committee_id, role)
    WHERE status = 'ACTIVE' AND role IN ('CHAIRPERSON', 'VICE_CHAIRPERSON', 'SECRETARY');

-- Tracked tasks: regulator notifications, validity extension requests, ...
CREATE TABLE tracked_task (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    institution_id  uuid        NOT NULL REFERENCES institution (id) ON DELETE RESTRICT,
    committee_id    uuid        REFERENCES committee (id) ON DELETE RESTRICT,
    kind            text        NOT NULL,
    title           text        NOT NULL,
    details         text,
    due_date        date,
    status          text        NOT NULL DEFAULT 'OPEN' CHECK (status IN ('OPEN', 'DONE', 'CANCELLED')),
    entity_type     text,
    entity_id       uuid,
    assignee_id     uuid        REFERENCES app_user (id) ON DELETE RESTRICT,
    created_by      uuid        REFERENCES app_user (id) ON DELETE RESTRICT,
    created_at      timestamptz NOT NULL DEFAULT now(),
    done_at         timestamptz,
    done_by         uuid        REFERENCES app_user (id) ON DELETE RESTRICT,
    done_reference  text,
    done_note       text,
    version         bigint      NOT NULL DEFAULT 0
);
CREATE INDEX ix_task_institution ON tracked_task (institution_id, status, due_date);
CREATE INDEX ix_task_committee ON tracked_task (committee_id);
CREATE INDEX ix_task_assignee ON tracked_task (assignee_id);
CREATE INDEX ix_task_created_by ON tracked_task (created_by);
CREATE INDEX ix_task_done_by ON tracked_task (done_by);
CREATE INDEX ix_task_entity ON tracked_task (entity_id);

CREATE TABLE membership_replacement (
    id                  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    institution_id      uuid        NOT NULL REFERENCES institution (id) ON DELETE RESTRICT,
    committee_id        uuid        NOT NULL REFERENCES committee (id) ON DELETE RESTRICT,
    scope               text        NOT NULL CHECK (scope IN ('SINGLE', 'ALL')),
    reason              text        NOT NULL CHECK (length(trim(reason)) >= 10),
    performed_by        uuid        NOT NULL REFERENCES app_user (id) ON DELETE RESTRICT,
    performed_at        timestamptz NOT NULL DEFAULT now(),
    regulator_task_id   uuid        REFERENCES tracked_task (id) ON DELETE RESTRICT
);
CREATE INDEX ix_replacement_institution ON membership_replacement (institution_id);
CREATE INDEX ix_replacement_committee ON membership_replacement (committee_id);
CREATE INDEX ix_replacement_performed_by ON membership_replacement (performed_by);
CREATE INDEX ix_replacement_task ON membership_replacement (regulator_task_id);

ALTER TABLE committee_membership
    ADD CONSTRAINT fk_membership_replacement
    FOREIGN KEY (replacement_id) REFERENCES membership_replacement (id) ON DELETE RESTRICT;
CREATE INDEX ix_membership_replacement ON committee_membership (replacement_id);
