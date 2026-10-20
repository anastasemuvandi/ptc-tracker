-- =====================================================================
-- Tenant isolation with row-level security, and least-privilege grants.
--
-- ptc_app  : the application. Sees only rows whose institution_id equals
--            the session variable app.institution_id, set from the
--            authenticated token at the start of every request.
-- ptc_auth : the auth module. Reads identities/memberships to build
--            tokens; owns credential and token tables. No domain access.
--
-- Tables are owned by the migration role, which is NOT used at runtime.
-- =====================================================================

-- ---------------------------------------------------------------------
-- Uniform tenant tables: one policy, full DML except where noted.
-- ---------------------------------------------------------------------
DO $$
DECLARE
    t text;
    tenant_tables text[] := ARRAY[
        'institution_counter', 'committee', 'committee_membership', 'tracked_task',
        'membership_replacement', 'contract', 'proc_file', 'file_stage_history',
        'file_reassignment', 'officer_assignment', 'table_request',
        'committee_session', 'agenda_item', 'attendance', 'absence_apology',
        'coi_declaration', 'decision', 'vote', 'cbm_response',
        'evaluation', 'evaluation_stage', 'bidder_result', 'abnormal_price_check',
        'admin_cure_request', 'due_diligence', 'clarification', 'checklist_response',
        'amendment_request', 'extension_request',
        'minutes', 'minutes_acknowledgement', 'notification', 'push_subscription'];
BEGIN
    FOREACH t IN ARRAY tenant_tables LOOP
        EXECUTE format('ALTER TABLE %I ENABLE ROW LEVEL SECURITY', t);
        EXECUTE format(
            'CREATE POLICY tenant_isolation ON %I TO ptc_app '
            'USING (institution_id = app_current_institution()) '
            'WITH CHECK (institution_id = app_current_institution())', t);
        EXECUTE format('GRANT SELECT, INSERT, UPDATE ON %I TO ptc_app', t);
    END LOOP;
END
$$;

-- Records that legitimately disappear: un-scheduled agenda items and
-- revoked push subscriptions.
GRANT DELETE ON agenda_item, push_subscription TO ptc_app;

-- Legally significant acts: never updated in place once written.
REVOKE UPDATE ON coi_declaration, cbm_response, membership_replacement,
    file_reassignment, file_stage_history, officer_assignment,
    minutes_acknowledgement FROM ptc_app;

-- ---------------------------------------------------------------------
-- Institution
-- ---------------------------------------------------------------------
ALTER TABLE institution ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_self ON institution TO ptc_app
    USING (id = app_current_institution() OR app_is_platform_admin())
    WITH CHECK (id = app_current_institution() OR app_is_platform_admin());
GRANT SELECT, INSERT, UPDATE ON institution TO ptc_app;
CREATE POLICY auth_read ON institution TO ptc_auth USING (true);
GRANT SELECT ON institution TO ptc_auth;

-- ---------------------------------------------------------------------
-- Users and institution roles. Identity writes go through the auth
-- module (they bump token_version); the application may only read and
-- edit profile fields.
-- ---------------------------------------------------------------------
ALTER TABLE app_user ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON app_user TO ptc_app
    USING (institution_id = app_current_institution() OR id = app_current_user())
    WITH CHECK (institution_id = app_current_institution());
GRANT SELECT ON app_user TO ptc_app;
GRANT UPDATE (full_name, job_title, phone, office_holder_code, is_procurement_officer, locale, version)
    ON app_user TO ptc_app;
CREATE POLICY auth_all ON app_user TO ptc_auth USING (true) WITH CHECK (true);
GRANT SELECT, INSERT, UPDATE ON app_user TO ptc_auth;

ALTER TABLE user_role ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON user_role TO ptc_app
    USING (institution_id = app_current_institution() OR user_id = app_current_user());
GRANT SELECT ON user_role TO ptc_app;
CREATE POLICY auth_all ON user_role TO ptc_auth USING (true) WITH CHECK (true);
GRANT SELECT, INSERT, DELETE ON user_role TO ptc_auth;

-- The auth module reads memberships to put committee roles in tokens.
CREATE POLICY auth_read ON committee TO ptc_auth USING (true);
CREATE POLICY auth_read ON committee_membership TO ptc_auth USING (true);
GRANT SELECT ON committee, committee_membership TO ptc_auth;

-- Credentials and tokens: auth module only.
DO $$
DECLARE
    t text;
BEGIN
    FOREACH t IN ARRAY ARRAY['user_credential', 'mfa_totp', 'mfa_recovery_code',
                             'refresh_token', 'auth_one_time_token', 'auth_step_up'] LOOP
        EXECUTE format('ALTER TABLE %I ENABLE ROW LEVEL SECURITY', t);
        EXECUTE format('CREATE POLICY auth_all ON %I TO ptc_auth USING (true) WITH CHECK (true)', t);
        EXECUTE format('GRANT SELECT, INSERT, UPDATE, DELETE ON %I TO ptc_auth', t);
    END LOOP;
END
$$;

-- ---------------------------------------------------------------------
-- Configuration and holidays: platform rows are visible to everyone;
-- institution rows only to that institution. Configuration history is
-- never edited — superseding rows are inserted.
-- ---------------------------------------------------------------------
ALTER TABLE config_rule ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_or_platform ON config_rule TO ptc_app
    USING (institution_id IS NULL OR institution_id = app_current_institution())
    WITH CHECK (institution_id = app_current_institution()
                OR (institution_id IS NULL AND app_is_platform_admin()));
GRANT SELECT, INSERT ON config_rule TO ptc_app;
CREATE POLICY auth_read ON config_rule TO ptc_auth USING (true);
GRANT SELECT ON config_rule TO ptc_auth;

ALTER TABLE holiday ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_or_platform ON holiday TO ptc_app
    USING (institution_id IS NULL OR institution_id = app_current_institution())
    WITH CHECK (institution_id = app_current_institution()
                OR (institution_id IS NULL AND app_is_platform_admin()));
GRANT SELECT, INSERT, DELETE ON holiday TO ptc_app;

GRANT SELECT ON file_type TO ptc_app, ptc_auth;

-- ---------------------------------------------------------------------
-- Audit trail: insert and read only. UPDATE/DELETE/TRUNCATE are not
-- granted to anyone at runtime, and the trigger in V1 rejects them even
-- for the owner.
-- ---------------------------------------------------------------------
ALTER TABLE audit_event ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_read ON audit_event FOR SELECT TO ptc_app
    USING (institution_id = app_current_institution());
CREATE POLICY tenant_append ON audit_event FOR INSERT TO ptc_app
    WITH CHECK (institution_id IS NOT DISTINCT FROM app_current_institution());
CREATE POLICY auth_append ON audit_event FOR INSERT TO ptc_auth WITH CHECK (true);
REVOKE ALL ON audit_event FROM PUBLIC;
GRANT SELECT, INSERT ON audit_event TO ptc_app;
GRANT INSERT ON audit_event TO ptc_auth;

-- ---------------------------------------------------------------------
-- Idempotency records are private to the calling user.
-- ---------------------------------------------------------------------
ALTER TABLE idempotency_record ENABLE ROW LEVEL SECURITY;
CREATE POLICY own_records ON idempotency_record TO ptc_app
    USING (user_id = app_current_user())
    WITH CHECK (user_id = app_current_user());
-- DELETE: a failed request releases its key so the client can retry.
GRANT SELECT, INSERT, UPDATE, DELETE ON idempotency_record TO ptc_app;

-- ---------------------------------------------------------------------
-- Narrow cross-tenant entry points (SECURITY DEFINER, owner bypasses RLS).
-- ---------------------------------------------------------------------

-- Background jobs iterate institutions and then set app.institution_id.
CREATE FUNCTION app_active_institution_ids() RETURNS SETOF uuid
    LANGUAGE sql STABLE SECURITY DEFINER SET search_path = public AS
$$ SELECT id FROM institution WHERE status = 'ACTIVE' ORDER BY abbreviation $$;

-- Platform administrator: operational metrics only, never file content.
CREATE FUNCTION platform_metrics()
    RETURNS TABLE (institution_id uuid, name text, abbreviation text, status text,
                   committees bigint, active_users bigint, open_files bigint,
                   overdue_files bigint, sessions_last_30_days bigint,
                   last_activity timestamptz)
    LANGUAGE sql STABLE SECURITY DEFINER SET search_path = public AS
$$
    SELECT i.id, i.name, i.abbreviation, i.status,
           (SELECT count(*) FROM committee c WHERE c.institution_id = i.id AND c.status = 'ACTIVE'),
           (SELECT count(*) FROM app_user u WHERE u.institution_id = i.id AND u.status = 'ACTIVE'),
           (SELECT count(*) FROM proc_file f WHERE f.institution_id = i.id AND f.closed_at IS NULL),
           (SELECT count(*) FROM proc_file f WHERE f.institution_id = i.id AND f.closed_at IS NULL
                AND f.target_date < (now() AT TIME ZONE 'Africa/Kigali')::date),
           (SELECT count(*) FROM committee_session s WHERE s.institution_id = i.id
                AND s.status = 'HELD' AND s.scheduled_at > now() - interval '30 days'),
           (SELECT max(a.occurred_at) FROM audit_event a WHERE a.institution_id = i.id)
      FROM institution i
     WHERE app_is_platform_admin()
     ORDER BY i.abbreviation
$$;

REVOKE ALL ON FUNCTION app_active_institution_ids() FROM PUBLIC;
REVOKE ALL ON FUNCTION platform_metrics() FROM PUBLIC;
GRANT EXECUTE ON FUNCTION app_active_institution_ids() TO ptc_app;
GRANT EXECUTE ON FUNCTION platform_metrics() TO ptc_app;
GRANT EXECUTE ON FUNCTION app_current_institution(), app_current_user(), app_is_platform_admin()
    TO ptc_app, ptc_auth;
