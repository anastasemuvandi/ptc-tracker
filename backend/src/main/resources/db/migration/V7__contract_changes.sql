-- =====================================================================
-- Type C — contract amendment (addendum) and type D — time extension.
-- =====================================================================

CREATE TABLE amendment_request (
    id                          uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    institution_id              uuid          NOT NULL REFERENCES institution (id) ON DELETE RESTRICT,
    file_id                     uuid          NOT NULL REFERENCES proc_file (id) ON DELETE RESTRICT,
    contract_id                 uuid          NOT NULL REFERENCES contract (id) ON DELETE RESTRICT,
    -- Same currency as the contract; never converted.
    currency                    char(3)       NOT NULL,
    requested_value             numeric(20,2) NOT NULL,
    description                 text          NOT NULL,
    -- Qualitative test: absolute bar, cannot be cured by the quantitative test.
    affects_substance           boolean,
    substance_justification     text,
    deliverables_change         boolean,
    perf_security_extend        boolean,
    perf_security_increase      boolean,
    key_personnel_change        boolean,
    replacement_equal_or_better boolean,
    -- Linked type D file when a time extension carries a cost claim.
    linked_extension_file_id    uuid          REFERENCES proc_file (id) ON DELETE RESTRICT,
    -- Figures frozen when the committee issues its recommendation.
    assessment_snapshot         jsonb,
    status                      text          NOT NULL DEFAULT 'OPEN'
                                CHECK (status IN ('OPEN', 'RECOMMENDED', 'APPROVED', 'REJECTED', 'WITHDRAWN')),
    approved_value              numeric(20,2),
    approved_at                 timestamptz,
    created_by                  uuid          NOT NULL REFERENCES app_user (id) ON DELETE RESTRICT,
    created_at                  timestamptz   NOT NULL DEFAULT now(),
    version                     bigint        NOT NULL DEFAULT 0,
    CONSTRAINT uq_amendment_file UNIQUE (file_id),
    CONSTRAINT ck_substance_justified CHECK (affects_substance IS NULL OR length(trim(substance_justification)) > 0),
    CONSTRAINT ck_amendment_approved CHECK (status <> 'APPROVED' OR approved_value IS NOT NULL)
);
CREATE INDEX ix_amendment_institution ON amendment_request (institution_id);
CREATE INDEX ix_amendment_contract ON amendment_request (contract_id);
CREATE INDEX ix_amendment_linked_extension ON amendment_request (linked_extension_file_id);
CREATE INDEX ix_amendment_created_by ON amendment_request (created_by);

CREATE TABLE extension_request (
    id                          uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    institution_id              uuid        NOT NULL REFERENCES institution (id) ON DELETE RESTRICT,
    file_id                     uuid        NOT NULL REFERENCES proc_file (id) ON DELETE RESTRICT,
    contract_id                 uuid        NOT NULL REFERENCES contract (id) ON DELETE RESTRICT,
    completion_date_before      date        NOT NULL,
    requested_completion_date   date        NOT NULL,
    ground_code                 text        NOT NULL,
    ground_details              text        NOT NULL,
    request_received_on         date        NOT NULL,
    received_before_completion  boolean     GENERATED ALWAYS AS (request_received_on < completion_date_before) STORED,
    -- Bidder's fault: penalties may apply instead; reasoning if extended anyway.
    penalties_considered        boolean,
    bidder_fault_reasoning      text,
    has_cost_claim              boolean     NOT NULL DEFAULT false,
    linked_amendment_file_id    uuid        REFERENCES proc_file (id) ON DELETE RESTRICT,
    force_majeure_start         date,
    force_majeure_end           date,
    status                      text        NOT NULL DEFAULT 'OPEN'
                                CHECK (status IN ('OPEN', 'RECOMMENDED', 'APPROVED', 'REJECTED', 'WITHDRAWN')),
    approved_completion_date    date,
    approved_at                 timestamptz,
    created_by                  uuid        NOT NULL REFERENCES app_user (id) ON DELETE RESTRICT,
    created_at                  timestamptz NOT NULL DEFAULT now(),
    version                     bigint      NOT NULL DEFAULT 0,
    CONSTRAINT uq_extension_file UNIQUE (file_id),
    CONSTRAINT ck_extension_dates CHECK (requested_completion_date > completion_date_before),
    CONSTRAINT ck_extension_approved CHECK (status <> 'APPROVED' OR approved_completion_date IS NOT NULL)
);
CREATE INDEX ix_extension_institution ON extension_request (institution_id);
CREATE INDEX ix_extension_contract ON extension_request (contract_id);
CREATE INDEX ix_extension_linked_amendment ON extension_request (linked_amendment_file_id);
CREATE INDEX ix_extension_created_by ON extension_request (created_by);
