-- =====================================================================
-- Requests to the committee. Employees outside the committee (requesters)
-- are onboarded by the institution administrator, short term (with an end
-- of access, after which the account is disabled) or long term. They
-- submit requests (amendment, extension, request for a recommendation)
-- with internal supporting documents, declaring that these contain no bid
-- documents or bidder prices; Umucyo stays the system of record for those.
-- A committee officer accepts a request (it becomes a file, which can then
-- be put on a session agenda) or returns it with a reason.
-- =====================================================================

ALTER TABLE user_role DROP CONSTRAINT user_role_role_check;
ALTER TABLE user_role ADD CONSTRAINT user_role_role_check CHECK (role IN
    ('PLATFORM_ADMIN', 'INSTITUTION_ADMIN', 'CBM', 'READ_ONLY', 'REQUESTER'));

ALTER TABLE app_user
    ADD COLUMN requester_category text CHECK (requester_category IN ('SHORT_TERM', 'LONG_TERM')),
    ADD COLUMN access_until       date,
    ADD CONSTRAINT ck_app_user_short_term_end
        CHECK (requester_category IS DISTINCT FROM 'SHORT_TERM' OR access_until IS NOT NULL);
-- Requester access is set by the institution administrator through the application.
GRANT UPDATE (requester_category, access_until) ON app_user TO ptc_app;

INSERT INTO config_rule (institution_id, rule_key, rule_value, effective_from, reason) VALUES
(NULL, 'request.types',            '["C", "D", "H"]', '2020-01-01', 'File types a requester may submit'),
(NULL, 'request.minDocuments',     '1', '2020-01-01', 'Supporting documents required before submitting'),
(NULL, 'request.maxDocuments',     '10', '2020-01-01', 'Supporting documents allowed per request'),
(NULL, 'request.maxDocumentBytes', '10485760', '2020-01-01', 'Largest supporting document (10 MB)'),
(NULL, 'request.documentTypes',
 '["application/pdf", "application/vnd.openxmlformats-officedocument.wordprocessingml.document", "application/msword", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "image/jpeg", "image/png"]',
 '2020-01-01', 'Accepted supporting document formats');

CREATE TABLE ptc_request (
    id                      uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    institution_id          uuid        NOT NULL REFERENCES institution (id) ON DELETE RESTRICT,
    -- e.g. REQ-2026/0007, from institution_counter key REQ:<year>.
    request_number          text        NOT NULL,
    requester_id            uuid        NOT NULL REFERENCES app_user (id) ON DELETE RESTRICT,
    item_type               char(1)     NOT NULL CHECK (item_type IN ('A','B','C','D','E','F','G','H')),
    title                   text        NOT NULL,
    description             text        NOT NULL,
    contract_ref            text,
    tender_ref              text,
    status                  text        NOT NULL DEFAULT 'DRAFT'
                            CHECK (status IN ('DRAFT', 'SUBMITTED', 'ACCEPTED', 'RETURNED', 'WITHDRAWN')),
    -- "These documents contain no bid documents, bidder submissions or prices."
    declared_at             timestamptz,
    submitted_at            timestamptz,
    decided_by              uuid REFERENCES app_user (id) ON DELETE RESTRICT,
    decided_at              timestamptz,
    decision_note           text,
    committee_id            uuid REFERENCES committee (id) ON DELETE RESTRICT,
    file_id                 uuid REFERENCES proc_file (id) ON DELETE RESTRICT,
    created_at              timestamptz NOT NULL DEFAULT now(),
    version                 bigint      NOT NULL DEFAULT 0,
    UNIQUE (institution_id, request_number),
    CHECK (status IN ('DRAFT', 'WITHDRAWN') OR declared_at IS NOT NULL),
    CHECK (status <> 'ACCEPTED' OR file_id IS NOT NULL),
    CHECK (status <> 'RETURNED' OR length(trim(coalesce(decision_note, ''))) > 0)
);
CREATE INDEX ix_request_institution ON ptc_request (institution_id);
CREATE INDEX ix_request_requester ON ptc_request (requester_id, created_at DESC);
CREATE INDEX ix_request_status ON ptc_request (status, submitted_at);
CREATE INDEX ix_request_file ON ptc_request (file_id);
CREATE INDEX ix_request_committee ON ptc_request (committee_id);
CREATE INDEX ix_request_decided_by ON ptc_request (decided_by);

CREATE TABLE request_document (
    id                  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    institution_id      uuid        NOT NULL REFERENCES institution (id) ON DELETE RESTRICT,
    request_id          uuid        NOT NULL REFERENCES ptc_request (id) ON DELETE RESTRICT,
    file_name           text        NOT NULL,
    content_type        text        NOT NULL,
    size_bytes          bigint      NOT NULL CHECK (size_bytes > 0),
    sha256              text        NOT NULL,
    content             bytea       NOT NULL,
    uploaded_by         uuid        NOT NULL REFERENCES app_user (id) ON DELETE RESTRICT,
    uploaded_at         timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX ix_request_document_institution ON request_document (institution_id);
CREATE INDEX ix_request_document_request ON request_document (request_id);
CREATE INDEX ix_request_document_uploaded_by ON request_document (uploaded_by);

ALTER TABLE ptc_request ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON ptc_request TO ptc_app
    USING (institution_id = app_current_institution())
    WITH CHECK (institution_id = app_current_institution());
GRANT SELECT, INSERT, UPDATE ON ptc_request TO ptc_app;

ALTER TABLE request_document ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON request_document TO ptc_app
    USING (institution_id = app_current_institution())
    WITH CHECK (institution_id = app_current_institution());
-- Documents are never edited; a requester may remove one before submitting.
GRANT SELECT, INSERT, DELETE ON request_document TO ptc_app;
