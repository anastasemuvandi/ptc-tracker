-- =====================================================================
-- Type A — evaluation report review. Process only: no scores, prices or
-- bidder documents are stored. Bidders are referenced by reference only.
-- Also: generic checklist responses (type B and others).
-- =====================================================================

CREATE TABLE evaluation (
    id                          uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    institution_id              uuid        NOT NULL REFERENCES institution (id) ON DELETE RESTRICT,
    file_id                     uuid        NOT NULL REFERENCES proc_file (id) ON DELETE RESTRICT,
    -- Criteria lock: evaluation only against the published criteria.
    criteria_locked_at          timestamptz,
    criteria_locked_by          uuid        REFERENCES app_user (id) ON DELETE RESTRICT,
    criteria_defect_findings    text,
    -- Stage definitions resolved when the evaluation was opened.
    config_snapshot             jsonb       NOT NULL,
    created_at                  timestamptz NOT NULL DEFAULT now(),
    version                     bigint      NOT NULL DEFAULT 0,
    CONSTRAINT uq_evaluation_file UNIQUE (file_id)
);
CREATE INDEX ix_evaluation_institution ON evaluation (institution_id);
CREATE INDEX ix_evaluation_locked_by ON evaluation (criteria_locked_by);

CREATE TABLE evaluation_stage (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    institution_id  uuid        NOT NULL REFERENCES institution (id) ON DELETE RESTRICT,
    evaluation_id   uuid        NOT NULL REFERENCES evaluation (id) ON DELETE RESTRICT,
    file_id         uuid        NOT NULL REFERENCES proc_file (id) ON DELETE RESTRICT,
    stage_code      text        NOT NULL,
    sequence        integer     NOT NULL,
    status          text        NOT NULL DEFAULT 'NOT_STARTED'
                    CHECK (status IN ('NOT_STARTED', 'IN_PROGRESS', 'COMPLETED')),
    outcome         text,
    -- Stage-level structured record (counts, resource person, tax basis ...).
    data            jsonb       NOT NULL DEFAULT '{}'::jsonb,
    started_at      timestamptz,
    completed_at    timestamptz,
    completed_by    uuid        REFERENCES app_user (id) ON DELETE RESTRICT,
    version         bigint      NOT NULL DEFAULT 0,
    CONSTRAINT uq_evaluation_stage UNIQUE (evaluation_id, stage_code)
);
CREATE INDEX ix_eval_stage_institution ON evaluation_stage (institution_id);
CREATE INDEX ix_eval_stage_file ON evaluation_stage (file_id);
CREATE INDEX ix_eval_stage_completed_by ON evaluation_stage (completed_by);

-- Per-bidder results for A1 (pass/fail + ground), A2 (fail criterion +
-- reason, rank), A3 (arithmetic disqualification, rank).
CREATE TABLE bidder_result (
    id                      uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    institution_id          uuid        NOT NULL REFERENCES institution (id) ON DELETE RESTRICT,
    stage_id                uuid        NOT NULL REFERENCES evaluation_stage (id) ON DELETE RESTRICT,
    file_id                 uuid        NOT NULL REFERENCES proc_file (id) ON DELETE RESTRICT,
    bidder_ref              text        NOT NULL,
    result                  text        NOT NULL CHECK (result IN ('PASS', 'FAIL')),
    -- Code of the requirement/criterion not met (e.g. BID_SECURITY).
    failed_requirement      text,
    -- Committee's reason; "non-compliant" alone is not accepted.
    failure_reason          text,
    rank                    integer     CHECK (rank IS NULL OR rank > 0),
    recorded_by             uuid        NOT NULL REFERENCES app_user (id) ON DELETE RESTRICT,
    recorded_at             timestamptz NOT NULL DEFAULT now(),
    version                 bigint      NOT NULL DEFAULT 0,
    CONSTRAINT uq_bidder_result UNIQUE (stage_id, bidder_ref),
    CONSTRAINT ck_bidder_failure CHECK (result = 'PASS' OR
        (failed_requirement IS NOT NULL AND length(trim(failure_reason)) >= 10))
);
CREATE INDEX ix_bidder_result_institution ON bidder_result (institution_id);
CREATE INDEX ix_bidder_result_file ON bidder_result (file_id);
CREATE INDEX ix_bidder_result_recorded_by ON bidder_result (recorded_by);

-- A3 abnormal price check. Rejection requires the written request and the
-- committee's analysis — enforced here as well as in the service.
CREATE TABLE abnormal_price_check (
    id                      uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    institution_id          uuid        NOT NULL REFERENCES institution (id) ON DELETE RESTRICT,
    file_id                 uuid        NOT NULL REFERENCES proc_file (id) ON DELETE RESTRICT,
    bidder_ref              text        NOT NULL,
    direction               text        NOT NULL CHECK (direction IN ('ABNORMALLY_HIGH', 'ABNORMALLY_LOW')),
    request_ref             text,
    request_date            date,
    explanation_summary     text,
    explanation_date        date,
    analysis                text,
    doubt_lifted            boolean,
    decision                text        NOT NULL DEFAULT 'PENDING'
                            CHECK (decision IN ('PENDING', 'ACCEPTED', 'REJECTED')),
    decision_reasons        text,
    recorded_by             uuid        NOT NULL REFERENCES app_user (id) ON DELETE RESTRICT,
    recorded_at             timestamptz NOT NULL DEFAULT now(),
    version                 bigint      NOT NULL DEFAULT 0,
    CONSTRAINT ck_abnormal_rejection CHECK (decision <> 'REJECTED' OR (
        request_date IS NOT NULL AND request_ref IS NOT NULL
        AND length(trim(analysis)) > 0 AND doubt_lifted IS FALSE
        AND length(trim(decision_reasons)) > 0)),
    CONSTRAINT ck_abnormal_decided CHECK (decision = 'PENDING' OR length(trim(decision_reasons)) > 0)
);
CREATE INDEX ix_abnormal_institution ON abnormal_price_check (institution_id);
CREATE INDEX ix_abnormal_file ON abnormal_price_check (file_id);
CREATE INDEX ix_abnormal_recorded_by ON abnormal_price_check (recorded_by);

-- A4 administrative compliance of the first-ranked bidder (cure period).
CREATE TABLE admin_cure_request (
    id                  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    institution_id      uuid        NOT NULL REFERENCES institution (id) ON DELETE RESTRICT,
    file_id             uuid        NOT NULL REFERENCES proc_file (id) ON DELETE RESTRICT,
    bidder_ref          text        NOT NULL,
    -- [{code, label, supplied, suppliedOn, note}]
    outstanding_items   jsonb       NOT NULL,
    issued_on           date        NOT NULL,
    working_days        integer     NOT NULL,
    deadline            date        NOT NULL,
    supplied_summary    text,
    supplied_on         date,
    outcome             text        NOT NULL DEFAULT 'PENDING'
                        CHECK (outcome IN ('PENDING', 'COMPLIED', 'NOT_COMPLIED')),
    outcome_note        text,
    recorded_by         uuid        NOT NULL REFERENCES app_user (id) ON DELETE RESTRICT,
    recorded_at         timestamptz NOT NULL DEFAULT now(),
    version             bigint      NOT NULL DEFAULT 0
);
CREATE INDEX ix_cure_institution ON admin_cure_request (institution_id);
CREATE INDEX ix_cure_file ON admin_cure_request (file_id);
CREATE INDEX ix_cure_recorded_by ON admin_cure_request (recorded_by);

-- A5 due diligence on the successful bidder.
CREATE TABLE due_diligence (
    id                  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    institution_id      uuid        NOT NULL REFERENCES institution (id) ON DELETE RESTRICT,
    file_id             uuid        NOT NULL REFERENCES proc_file (id) ON DELETE RESTRICT,
    bidder_ref          text        NOT NULL,
    performed_by_name   text        NOT NULL,
    performed_on        date        NOT NULL,
    items_verified      text        NOT NULL,
    result              text        NOT NULL CHECK (result IN ('CONFIRMED', 'FALSE', 'MATERIALLY_CONFUSING', 'INCOMPLETE')),
    disqualified        boolean     NOT NULL DEFAULT false,
    disqualification_ground text,
    recorded_by         uuid        NOT NULL REFERENCES app_user (id) ON DELETE RESTRICT,
    recorded_at         timestamptz NOT NULL DEFAULT now(),
    version             bigint      NOT NULL DEFAULT 0,
    CONSTRAINT ck_dd_disqualification CHECK (
        (result = 'CONFIRMED' AND NOT disqualified) OR
        (result <> 'CONFIRMED' AND (NOT disqualified OR length(trim(disqualification_ground)) > 0)))
);
CREATE INDEX ix_dd_institution ON due_diligence (institution_id);
CREATE INDEX ix_dd_file ON due_diligence (file_id);
CREATE INDEX ix_dd_recorded_by ON due_diligence (recorded_by);

-- Clarification log: a bidder may explain, not improve.
CREATE TABLE clarification (
    id                      uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    institution_id          uuid        NOT NULL REFERENCES institution (id) ON DELETE RESTRICT,
    file_id                 uuid        NOT NULL REFERENCES proc_file (id) ON DELETE RESTRICT,
    bidder_ref              text        NOT NULL,
    issued_on               date        NOT NULL,
    subject                 text        NOT NULL,
    response_received_on    date,
    treatment               text,
    effect                  text        NOT NULL DEFAULT 'PENDING'
                            CHECK (effect IN ('PENDING', 'CLARIFIED', 'ATTEMPTED_ALTERATION')),
    recorded_by             uuid        NOT NULL REFERENCES app_user (id) ON DELETE RESTRICT,
    recorded_at             timestamptz NOT NULL DEFAULT now(),
    version                 bigint      NOT NULL DEFAULT 0,
    CONSTRAINT ck_clarification_effect CHECK (effect = 'PENDING' OR
        (response_received_on IS NOT NULL AND length(trim(treatment)) > 0))
);
CREATE INDEX ix_clarification_institution ON clarification (institution_id);
CREATE INDEX ix_clarification_file ON clarification (file_id);
CREATE INDEX ix_clarification_recorded_by ON clarification (recorded_by);

-- Checklist answers (type B prompts and any configured type checklist).
CREATE TABLE checklist_response (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    institution_id  uuid        NOT NULL REFERENCES institution (id) ON DELETE RESTRICT,
    file_id         uuid        NOT NULL REFERENCES proc_file (id) ON DELETE RESTRICT,
    item_code       text        NOT NULL,
    answer          text        NOT NULL CHECK (answer IN ('YES', 'NO', 'NOT_APPLICABLE')),
    note            text,
    answered_by     uuid        NOT NULL REFERENCES app_user (id) ON DELETE RESTRICT,
    answered_at     timestamptz NOT NULL DEFAULT now(),
    version         bigint      NOT NULL DEFAULT 0,
    CONSTRAINT uq_checklist_response UNIQUE (file_id, item_code)
);
CREATE INDEX ix_checklist_institution ON checklist_response (institution_id);
CREATE INDEX ix_checklist_answered_by ON checklist_response (answered_by);
