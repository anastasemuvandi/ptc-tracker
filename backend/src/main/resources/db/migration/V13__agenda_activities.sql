-- =====================================================================
-- Agenda activities. When a file is put on a session agenda, the officer
-- names what the committee will do with it in that sitting (e.g. the
-- preliminary examination, the amendment review). Each activity is then
-- recorded as completed or not completed; one not completed is carried
-- over to a later session of the committee once.
-- For type A the codes are the evaluation stage codes, kept in step with
-- the evaluation; RECOMMENDATION (and A6) complete when the vote adopts it.
-- =====================================================================

INSERT INTO config_rule (institution_id, rule_key, rule_value, effective_from, reason) VALUES
(NULL, 'agenda.activities', '{
  "A": ["A1", "A2", "A3", "A4", "A5", "A6"],
  "B": ["DOCUMENT_REVIEW", "RECOMMENDATION"],
  "C": ["AMENDMENT_REVIEW", "RECOMMENDATION"],
  "D": ["EXTENSION_REVIEW", "RECOMMENDATION"],
  "E": ["PREQUALIFICATION_REVIEW", "RECOMMENDATION"],
  "F": ["CANCELLATION_REVIEW", "RECOMMENDATION"],
  "G": ["FRAMEWORK_EVALUATION", "RECOMMENDATION"],
  "H": ["REFERRAL_REVIEW", "RECOMMENDATION"]
}', '2020-01-01', 'Platform default: activities that may be scheduled per file type');

CREATE TABLE agenda_activity (
    id                  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    institution_id      uuid        NOT NULL REFERENCES institution (id) ON DELETE RESTRICT,
    session_id          uuid        NOT NULL REFERENCES committee_session (id) ON DELETE RESTRICT,
    agenda_item_id      uuid        NOT NULL REFERENCES agenda_item (id) ON DELETE RESTRICT,
    file_id             uuid        NOT NULL REFERENCES proc_file (id) ON DELETE RESTRICT,
    activity_code       text        NOT NULL,
    position            integer     NOT NULL,
    status              text        NOT NULL DEFAULT 'PLANNED'
                        CHECK (status IN ('PLANNED', 'COMPLETED', 'NOT_COMPLETED')),
    note                text,
    recorded_at         timestamptz,
    recorded_by         uuid REFERENCES app_user (id) ON DELETE RESTRICT,
    carried_from_id     uuid REFERENCES agenda_activity (id) ON DELETE RESTRICT,
    created_at          timestamptz NOT NULL DEFAULT now(),
    version             bigint      NOT NULL DEFAULT 0,
    UNIQUE (agenda_item_id, activity_code),
    CHECK (status <> 'NOT_COMPLETED' OR length(trim(coalesce(note, ''))) > 0)
);
CREATE INDEX ix_activity_institution ON agenda_activity (institution_id);
CREATE INDEX ix_activity_session ON agenda_activity (session_id);
CREATE INDEX ix_activity_item ON agenda_activity (agenda_item_id, position);
CREATE INDEX ix_activity_file ON agenda_activity (file_id, activity_code);
CREATE INDEX ix_activity_recorded_by ON agenda_activity (recorded_by);
-- An activity is carried over at most once.
CREATE UNIQUE INDEX ux_activity_carried_from ON agenda_activity (carried_from_id) WHERE carried_from_id IS NOT NULL;

ALTER TABLE agenda_activity ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON agenda_activity TO ptc_app
    USING (institution_id = app_current_institution())
    WITH CHECK (institution_id = app_current_institution());
-- DELETE: activities go with an agenda item removed before the session.
GRANT SELECT, INSERT, UPDATE, DELETE ON agenda_activity TO ptc_app;
