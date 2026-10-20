-- =====================================================================
-- Contracts (referenced, not managed: the system of record is Umucyo)
-- and the committee files register.
-- =====================================================================

CREATE TABLE contract (
    id                          uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    institution_id              uuid          NOT NULL REFERENCES institution (id) ON DELETE RESTRICT,
    contract_ref                text          NOT NULL,
    tender_ref                  text,
    title                       text          NOT NULL,
    supplier_ref                text,
    procurement_type            text          NOT NULL CHECK (procurement_type IN
                                ('WORKS', 'GOODS', 'CONSULTANCY', 'NON_CONSULTANCY')),
    currency                    char(3)       NOT NULL,
    original_value              numeric(20,2) NOT NULL CHECK (original_value > 0),
    signed_date                 date,
    original_completion_date    date          NOT NULL,
    current_completion_date     date          NOT NULL,
    perf_security_ref           text,
    perf_security_amount        numeric(20,2),
    perf_security_currency      char(3),
    perf_security_expiry        date,
    is_framework                boolean       NOT NULL DEFAULT false,
    framework_expiry            date,
    status                      text          NOT NULL DEFAULT 'ACTIVE'
                                CHECK (status IN ('ACTIVE', 'COMPLETED', 'TERMINATED', 'CANCELLED')),
    created_by                  uuid          REFERENCES app_user (id) ON DELETE RESTRICT,
    created_at                  timestamptz   NOT NULL DEFAULT now(),
    version                     bigint        NOT NULL DEFAULT 0,
    CONSTRAINT uq_contract_ref UNIQUE (institution_id, contract_ref)
);
CREATE INDEX ix_contract_institution ON contract (institution_id);
CREATE INDEX ix_contract_created_by ON contract (created_by);

-- Item types A..I (platform reference data, see V10 seed). proc_file holds
-- the code with a CHECK constraint so that every foreign key stays uuid.
CREATE TABLE file_type (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    code            char(1) NOT NULL,
    name_key        text    NOT NULL,
    -- Administrative items (type I) may never carry a recommendation.
    can_recommend   boolean NOT NULL,
    needs_file      boolean NOT NULL,
    needs_contract  boolean NOT NULL,
    CONSTRAINT uq_file_type_code UNIQUE (code)
);

CREATE TABLE proc_file (
    id                      uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    institution_id          uuid        NOT NULL REFERENCES institution (id) ON DELETE RESTRICT,
    committee_id            uuid        NOT NULL REFERENCES committee (id) ON DELETE RESTRICT,
    -- Human-facing number from institution_counter, e.g. "F-2026/0042".
    file_number             text        NOT NULL,
    item_type               char(1)     NOT NULL CHECK (item_type IN ('A','B','C','D','E','F','G','H','I')),
    tender_ref              text,
    contract_id             uuid        REFERENCES contract (id) ON DELETE RESTRICT,
    title                   text        NOT NULL,
    procurement_type        text        CHECK (procurement_type IN
                            ('WORKS', 'GOODS', 'CONSULTANCY', 'NON_CONSULTANCY')),
    procurement_method      text,
    procurement_officer_id  uuid        REFERENCES app_user (id) ON DELETE RESTRICT,
    stage                   text        NOT NULL DEFAULT 'RECEIVED',
    date_received           date        NOT NULL,
    target_date             date,
    -- Type H: referral by the CBM.
    referral_ref            text,
    referral_reason         text,
    -- Type A: bid validity clock starts when the file is opened.
    bid_validity_expiry     date,
    opened_at               timestamptz,
    closed_at               timestamptz,
    created_by              uuid        REFERENCES app_user (id) ON DELETE RESTRICT,
    created_at              timestamptz NOT NULL DEFAULT now(),
    version                 bigint      NOT NULL DEFAULT 0,
    CONSTRAINT uq_file_number UNIQUE (institution_id, file_number),
    CONSTRAINT ck_referral CHECK (item_type <> 'H' OR
        (referral_ref IS NOT NULL AND length(trim(referral_reason)) > 0)),
    CONSTRAINT ck_contract_files CHECK (item_type NOT IN ('C', 'D', 'G') OR contract_id IS NOT NULL)
);
CREATE INDEX ix_file_institution ON proc_file (institution_id);
CREATE INDEX ix_file_committee ON proc_file (committee_id, stage);
CREATE INDEX ix_file_contract ON proc_file (contract_id);
CREATE INDEX ix_file_officer ON proc_file (procurement_officer_id);
CREATE INDEX ix_file_type ON proc_file (item_type);
CREATE INDEX ix_file_created_by ON proc_file (created_by);

CREATE TABLE file_stage_history (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    institution_id  uuid        NOT NULL REFERENCES institution (id) ON DELETE RESTRICT,
    file_id         uuid        NOT NULL REFERENCES proc_file (id) ON DELETE RESTRICT,
    from_stage      text,
    to_stage        text        NOT NULL,
    note            text,
    changed_by      uuid        REFERENCES app_user (id) ON DELETE RESTRICT,
    changed_at      timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX ix_stage_history_institution ON file_stage_history (institution_id);
CREATE INDEX ix_stage_history_file ON file_stage_history (file_id);
CREATE INDEX ix_stage_history_changed_by ON file_stage_history (changed_by);

CREATE TABLE file_reassignment (
    id                  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    institution_id      uuid        NOT NULL REFERENCES institution (id) ON DELETE RESTRICT,
    file_id             uuid        NOT NULL REFERENCES proc_file (id) ON DELETE RESTRICT,
    from_committee_id   uuid        NOT NULL REFERENCES committee (id) ON DELETE RESTRICT,
    to_committee_id     uuid        NOT NULL REFERENCES committee (id) ON DELETE RESTRICT,
    reason              text        NOT NULL CHECK (length(trim(reason)) > 0),
    performed_by        uuid        NOT NULL REFERENCES app_user (id) ON DELETE RESTRICT,
    performed_at        timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX ix_reassignment_institution ON file_reassignment (institution_id);
CREATE INDEX ix_reassignment_file ON file_reassignment (file_id);
CREATE INDEX ix_reassignment_from ON file_reassignment (from_committee_id);
CREATE INDEX ix_reassignment_to ON file_reassignment (to_committee_id);
CREATE INDEX ix_reassignment_by ON file_reassignment (performed_by);

-- Procurement officer assignment per financial year (by the CBM).
CREATE TABLE officer_assignment (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    institution_id  uuid        NOT NULL REFERENCES institution (id) ON DELETE RESTRICT,
    file_id         uuid        NOT NULL REFERENCES proc_file (id) ON DELETE RESTRICT,
    officer_id      uuid        NOT NULL REFERENCES app_user (id) ON DELETE RESTRICT,
    financial_year  text        NOT NULL,
    assigned_by     uuid        NOT NULL REFERENCES app_user (id) ON DELETE RESTRICT,
    assigned_at     timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX ix_officer_assignment_institution ON officer_assignment (institution_id);
CREATE INDEX ix_officer_assignment_file ON officer_assignment (file_id);
CREATE INDEX ix_officer_assignment_officer ON officer_assignment (officer_id);
CREATE INDEX ix_officer_assignment_by ON officer_assignment (assigned_by);

-- Ordinary members request that a file be tabled; the Chairperson decides.
CREATE TABLE table_request (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    institution_id  uuid        NOT NULL REFERENCES institution (id) ON DELETE RESTRICT,
    committee_id    uuid        NOT NULL REFERENCES committee (id) ON DELETE RESTRICT,
    file_id         uuid        NOT NULL REFERENCES proc_file (id) ON DELETE RESTRICT,
    requested_by    uuid        NOT NULL REFERENCES app_user (id) ON DELETE RESTRICT,
    requested_at    timestamptz NOT NULL DEFAULT now(),
    reason          text,
    status          text        NOT NULL DEFAULT 'PENDING'
                    CHECK (status IN ('PENDING', 'ACCEPTED', 'DECLINED')),
    decided_by      uuid        REFERENCES app_user (id) ON DELETE RESTRICT,
    decided_at      timestamptz,
    decision_note   text,
    version         bigint      NOT NULL DEFAULT 0
);
CREATE INDEX ix_table_request_institution ON table_request (institution_id);
CREATE INDEX ix_table_request_committee ON table_request (committee_id, status);
CREATE INDEX ix_table_request_file ON table_request (file_id);
CREATE INDEX ix_table_request_requested_by ON table_request (requested_by);
CREATE INDEX ix_table_request_decided_by ON table_request (decided_by);
