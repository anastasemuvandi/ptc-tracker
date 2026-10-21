-- =====================================================================
-- Committee periods. A committee keeps its name; each appointment of the
-- committee (constitution, replacement of all members, reappointment)
-- opens a new period whose end follows from committee.periodMonths.
-- =====================================================================

INSERT INTO config_rule (institution_id, rule_key, rule_value, effective_from, reason) VALUES
(NULL, 'committee.periodMonths', '36', '2020-01-01', 'Platform default: a committee is appointed for three years');

CREATE TABLE committee_period (
    id                  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    institution_id      uuid        NOT NULL REFERENCES institution (id) ON DELETE RESTRICT,
    committee_id        uuid        NOT NULL REFERENCES committee (id) ON DELETE RESTRICT,
    starts_on           date        NOT NULL,
    ends_on             date        NOT NULL,
    appointment_ref     text,
    -- Set when the next period opens before this one has run its course.
    closed_on           date,
    opened_by           uuid REFERENCES app_user (id) ON DELETE RESTRICT,
    opened_at           timestamptz NOT NULL DEFAULT now(),
    CHECK (ends_on > starts_on),
    CHECK (closed_on IS NULL OR closed_on >= starts_on)
);
CREATE INDEX ix_committee_period_institution ON committee_period (institution_id);
CREATE INDEX ix_committee_period_committee ON committee_period (committee_id, starts_on DESC);
CREATE INDEX ix_committee_period_opened_by ON committee_period (opened_by);

ALTER TABLE committee_period ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON committee_period TO ptc_app
    USING (institution_id = app_current_institution())
    WITH CHECK (institution_id = app_current_institution());
GRANT SELECT, INSERT, UPDATE ON committee_period TO ptc_app;

-- Existing committees: one period from their first appointment (or creation).
INSERT INTO committee_period (institution_id, committee_id, starts_on, ends_on, opened_by, opened_at)
SELECT c.institution_id, c.id, s.starts_on, (s.starts_on + interval '36 months' - interval '1 day')::date,
       c.created_by, c.created_at
  FROM committee c
  CROSS JOIN LATERAL (
      SELECT coalesce(min(m.term_start), (c.created_at AT TIME ZONE 'Africa/Kigali')::date) AS starts_on
        FROM committee_membership m WHERE m.committee_id = c.id) s;
