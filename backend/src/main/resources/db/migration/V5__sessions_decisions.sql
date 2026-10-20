-- =====================================================================
-- Sessions, agenda, attendance, apologies, conflict of interest,
-- decisions, votes (with dissent) and the CBM's response.
-- =====================================================================

CREATE TABLE committee_session (
    id                  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    institution_id      uuid        NOT NULL REFERENCES institution (id) ON DELETE RESTRICT,
    committee_id        uuid        NOT NULL REFERENCES committee (id) ON DELETE RESTRICT,
    -- Human-facing number from institution_counter, e.g. "PTC-A 2026/014".
    session_number      text        NOT NULL,
    kind                text        NOT NULL CHECK (kind IN ('ORDINARY', 'EXTRAORDINARY')),
    scheduled_at        timestamptz NOT NULL,
    location            text,
    status              text        NOT NULL DEFAULT 'PLANNED'
                        CHECK (status IN ('PLANNED', 'IN_PROGRESS', 'HELD', 'CANCELLED')),
    convened_at         timestamptz NOT NULL DEFAULT now(),
    short_notice        boolean     NOT NULL DEFAULT false,
    short_notice_reason text,
    opened_at           timestamptz,
    closed_at           timestamptz,
    -- Size of the committee when the session was convened (quorum base).
    committee_size      integer     NOT NULL,
    config_snapshot     jsonb       NOT NULL,
    created_by          uuid        REFERENCES app_user (id) ON DELETE RESTRICT,
    created_at          timestamptz NOT NULL DEFAULT now(),
    version             bigint      NOT NULL DEFAULT 0,
    CONSTRAINT uq_session_number UNIQUE (institution_id, session_number),
    CONSTRAINT ck_short_notice_reason CHECK (NOT short_notice OR length(trim(short_notice_reason)) > 0)
);
CREATE INDEX ix_session_institution ON committee_session (institution_id);
CREATE INDEX ix_session_committee ON committee_session (committee_id, scheduled_at);
CREATE INDEX ix_session_created_by ON committee_session (created_by);

CREATE TABLE agenda_item (
    id                  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    institution_id      uuid        NOT NULL REFERENCES institution (id) ON DELETE RESTRICT,
    session_id          uuid        NOT NULL REFERENCES committee_session (id) ON DELETE RESTRICT,
    file_id             uuid        REFERENCES proc_file (id) ON DELETE RESTRICT,
    item_type           char(1)     NOT NULL CHECK (item_type IN ('A','B','C','D','E','F','G','H','I')),
    position            integer     NOT NULL,
    -- Type I only: ADOPT_MINUTES, WORK_PLANNING, MEMBER_INDUCTION.
    admin_kind          text        CHECK (admin_kind IN ('ADOPT_MINUTES', 'WORK_PLANNING', 'MEMBER_INDUCTION')),
    admin_note          text,
    status              text        NOT NULL DEFAULT 'PENDING'
                        CHECK (status IN ('PENDING', 'DISCUSSED', 'DEFERRED')),
    created_at          timestamptz NOT NULL DEFAULT now(),
    version             bigint      NOT NULL DEFAULT 0,
    -- Agenda scope is enforced: every item is a procurement file of a
    -- permitted type, except type I which is the committee's own business.
    CONSTRAINT ck_agenda_scope CHECK (
        (item_type = 'I' AND file_id IS NULL AND admin_kind IS NOT NULL) OR
        (item_type <> 'I' AND file_id IS NOT NULL AND admin_kind IS NULL))
);
CREATE INDEX ix_agenda_institution ON agenda_item (institution_id);
CREATE INDEX ix_agenda_session ON agenda_item (session_id, position);
CREATE INDEX ix_agenda_file ON agenda_item (file_id);

CREATE TABLE attendance (
    id                  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    institution_id      uuid        NOT NULL REFERENCES institution (id) ON DELETE RESTRICT,
    session_id          uuid        NOT NULL REFERENCES committee_session (id) ON DELETE RESTRICT,
    membership_id       uuid        NOT NULL REFERENCES committee_membership (id) ON DELETE RESTRICT,
    user_id             uuid        NOT NULL REFERENCES app_user (id) ON DELETE RESTRICT,
    status              text        NOT NULL CHECK (status IN
                        ('PRESENT', 'ABSENT_WITH_PERMISSION', 'ON_LEAVE', 'ABSENT_UNJUSTIFIED')),
    recorded_by         uuid        NOT NULL REFERENCES app_user (id) ON DELETE RESTRICT,
    recorded_at         timestamptz NOT NULL DEFAULT now(),
    -- When the client captured the mark (offline queue); conflict detection.
    client_recorded_at  timestamptz,
    version             bigint      NOT NULL DEFAULT 0,
    CONSTRAINT uq_attendance UNIQUE (session_id, membership_id)
);
CREATE INDEX ix_attendance_institution ON attendance (institution_id);
CREATE INDEX ix_attendance_membership ON attendance (membership_id);
CREATE INDEX ix_attendance_user ON attendance (user_id);
CREATE INDEX ix_attendance_recorded_by ON attendance (recorded_by);

CREATE TABLE absence_apology (
    id                  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    institution_id      uuid        NOT NULL REFERENCES institution (id) ON DELETE RESTRICT,
    session_id          uuid        NOT NULL REFERENCES committee_session (id) ON DELETE RESTRICT,
    user_id             uuid        NOT NULL REFERENCES app_user (id) ON DELETE RESTRICT,
    kind                text        NOT NULL DEFAULT 'ABSENT_WITH_PERMISSION'
                        CHECK (kind IN ('ABSENT_WITH_PERMISSION', 'ON_LEAVE')),
    reason              text        NOT NULL,
    submitted_at        timestamptz NOT NULL DEFAULT now(),
    client_recorded_at  timestamptz,
    -- True when received before the session start.
    timely              boolean     NOT NULL,
    CONSTRAINT uq_apology UNIQUE (session_id, user_id)
);
CREATE INDEX ix_apology_institution ON absence_apology (institution_id);
CREATE INDEX ix_apology_user ON absence_apology (user_id);

CREATE TABLE coi_declaration (
    id                  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    institution_id      uuid        NOT NULL REFERENCES institution (id) ON DELETE RESTRICT,
    session_id          uuid        NOT NULL REFERENCES committee_session (id) ON DELETE RESTRICT,
    agenda_item_id      uuid        NOT NULL REFERENCES agenda_item (id) ON DELETE RESTRICT,
    file_id             uuid        NOT NULL REFERENCES proc_file (id) ON DELETE RESTRICT,
    user_id             uuid        NOT NULL REFERENCES app_user (id) ON DELETE RESTRICT,
    has_interest        boolean     NOT NULL,
    interest_kind       text        CHECK (interest_kind IN ('DIRECT', 'INDIRECT')),
    details             text,
    declared_at         timestamptz NOT NULL DEFAULT now(),
    step_up_verified_at timestamptz NOT NULL,
    CONSTRAINT uq_coi UNIQUE (agenda_item_id, user_id),
    CONSTRAINT ck_coi_detail CHECK (NOT has_interest OR (interest_kind IS NOT NULL AND length(trim(details)) > 0))
);
CREATE INDEX ix_coi_institution ON coi_declaration (institution_id);
CREATE INDEX ix_coi_session ON coi_declaration (session_id);
CREATE INDEX ix_coi_file ON coi_declaration (file_id);
CREATE INDEX ix_coi_user ON coi_declaration (user_id);

CREATE TABLE decision (
    id                      uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    institution_id          uuid          NOT NULL REFERENCES institution (id) ON DELETE RESTRICT,
    session_id              uuid          NOT NULL REFERENCES committee_session (id) ON DELETE RESTRICT,
    agenda_item_id          uuid          NOT NULL REFERENCES agenda_item (id) ON DELETE RESTRICT,
    file_id                 uuid          NOT NULL REFERENCES proc_file (id) ON DELETE RESTRICT,
    item_type               char(1)       NOT NULL CHECK (item_type IN ('A','B','C','D','E','F','G','H')),
    recommendation_code     text          NOT NULL,
    recommendation_text     text          NOT NULL,
    recommended_bidder_ref  text,
    contract_value          numeric(20,2),
    currency                char(3),
    -- Structured outcome for C/D/B (ground, route, checklist result ...).
    structured_outcome      jsonb,
    present_count           integer,
    eligible_voters         integer,
    required_votes          integer,
    votes_for               integer       NOT NULL DEFAULT 0,
    votes_against           integer       NOT NULL DEFAULT 0,
    votes_abstained         integer       NOT NULL DEFAULT 0,
    status                  text          NOT NULL DEFAULT 'DRAFT'
                            CHECK (status IN ('DRAFT', 'VOTING', 'ADOPTED', 'NOT_ADOPTED', 'ISSUED')),
    -- Resolved configuration this decision was judged under. Never
    -- re-resolved when displaying the record later.
    config_snapshot         jsonb         NOT NULL,
    voting_opened_at        timestamptz,
    voting_closed_at        timestamptz,
    issued_at               timestamptz,
    issued_by               uuid          REFERENCES app_user (id) ON DELETE RESTRICT,
    issued_step_up_at       timestamptz,
    created_by              uuid          REFERENCES app_user (id) ON DELETE RESTRICT,
    created_at              timestamptz   NOT NULL DEFAULT now(),
    version                 bigint        NOT NULL DEFAULT 0,
    CONSTRAINT uq_decision_agenda_item UNIQUE (agenda_item_id),
    CONSTRAINT ck_decision_money CHECK (contract_value IS NULL OR currency IS NOT NULL)
);
CREATE INDEX ix_decision_institution ON decision (institution_id);
CREATE INDEX ix_decision_session ON decision (session_id);
CREATE INDEX ix_decision_file ON decision (file_id);
CREATE INDEX ix_decision_issued_by ON decision (issued_by);
CREATE INDEX ix_decision_created_by ON decision (created_by);

CREATE TABLE vote (
    id                  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    institution_id      uuid        NOT NULL REFERENCES institution (id) ON DELETE RESTRICT,
    decision_id         uuid        NOT NULL REFERENCES decision (id) ON DELETE RESTRICT,
    membership_id       uuid        NOT NULL REFERENCES committee_membership (id) ON DELETE RESTRICT,
    user_id             uuid        NOT NULL REFERENCES app_user (id) ON DELETE RESTRICT,
    choice              text        NOT NULL CHECK (choice IN ('FOR', 'AGAINST', 'ABSTAIN')),
    -- Mandatory for AGAINST/ABSTAIN: protects the member from joint liability.
    dissent_reason      text,
    cast_at             timestamptz NOT NULL DEFAULT now(),
    step_up_verified_at timestamptz NOT NULL,
    version             bigint      NOT NULL DEFAULT 0,
    CONSTRAINT uq_vote UNIQUE (decision_id, user_id),
    CONSTRAINT ck_dissent_reason CHECK (choice = 'FOR' OR length(trim(dissent_reason)) > 0)
);
CREATE INDEX ix_vote_institution ON vote (institution_id);
CREATE INDEX ix_vote_membership ON vote (membership_id);
CREATE INDEX ix_vote_user ON vote (user_id);

-- The CBM's response is a separate, logged act; it never edits the decision.
CREATE TABLE cbm_response (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    institution_id  uuid        NOT NULL REFERENCES institution (id) ON DELETE RESTRICT,
    decision_id     uuid        NOT NULL REFERENCES decision (id) ON DELETE RESTRICT,
    file_id         uuid        NOT NULL REFERENCES proc_file (id) ON DELETE RESTRICT,
    response        text        NOT NULL CHECK (response IN ('APPROVED', 'RETURNED_FOR_CORRECTION', 'OTHER')),
    reasons         text,
    responded_by    uuid        NOT NULL REFERENCES app_user (id) ON DELETE RESTRICT,
    responded_at    timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT ck_cbm_reasons CHECK (response = 'APPROVED' OR length(trim(reasons)) > 0)
);
CREATE INDEX ix_cbm_response_institution ON cbm_response (institution_id);
CREATE INDEX ix_cbm_response_decision ON cbm_response (decision_id);
CREATE INDEX ix_cbm_response_file ON cbm_response (file_id);
CREATE INDEX ix_cbm_response_by ON cbm_response (responded_by);
