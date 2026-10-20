package rw.gov.ptc.dashboard;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import rw.gov.ptc.auth.api.AuthPrincipal;
import rw.gov.ptc.common.Rows;
import rw.gov.ptc.common.Time;
import rw.gov.ptc.config.ConfigKeys;
import rw.gov.ptc.config.ConfigService;
import rw.gov.ptc.config.ConfigSnapshot;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Read-only aggregates for the role dashboards. All queries run under RLS,
 * so every figure is confined to the caller's institution.
 */
@Service
@Transactional(readOnly = true)
public class DashboardService {

    private final JdbcTemplate jdbc;
    private final ConfigService config;

    public DashboardService(JdbcTemplate jdbc, ConfigService config) {
        this.jdbc = jdbc;
        this.config = config;
    }

    // ------------------------------------------------------------------ member

    public Map<String, Object> member(AuthPrincipal p) {
        UUID me = p.userId();
        Date today = Date.valueOf(Time.today());
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("myFiles", q("""
                SELECT f.id, f.file_number, f.item_type, f.title, f.stage, f.target_date, f.date_received,
                       c.code AS committee_code, f.procurement_officer_id = ? AS i_am_officer,
                       (f.target_date IS NOT NULL AND f.target_date < ?) AS overdue
                  FROM proc_file f JOIN committee c ON c.id = f.committee_id
                 WHERE f.closed_at IS NULL
                   AND (f.procurement_officer_id = ?
                        OR f.committee_id IN (SELECT committee_id FROM committee_membership
                                               WHERE user_id = ? AND status = 'ACTIVE'))
                 ORDER BY overdue DESC, f.target_date NULLS LAST
                 LIMIT 100
                """, me, today, me, me));
        out.put("mySessions", q("""
                SELECT s.id, s.session_number, s.kind, s.scheduled_at, s.location, s.status, c.code AS committee_code,
                       (SELECT count(*) FROM agenda_item ai WHERE ai.session_id = s.id) AS agenda_count,
                       (SELECT a.status FROM attendance a WHERE a.session_id = s.id AND a.user_id = ?) AS my_attendance,
                       EXISTS (SELECT 1 FROM absence_apology ap WHERE ap.session_id = s.id AND ap.user_id = ?) AS my_apology
                  FROM committee_session s JOIN committee c ON c.id = s.committee_id
                 WHERE s.status IN ('PLANNED', 'IN_PROGRESS') AND s.scheduled_at > now() - interval '1 day'
                   AND s.committee_id IN (SELECT committee_id FROM committee_membership
                                           WHERE user_id = ? AND status = 'ACTIVE')
                 ORDER BY s.scheduled_at
                """, me, me, me));
        out.put("myPendingDeclarations", q("""
                SELECT ai.id AS agenda_item_id, f.file_number, f.title, s.session_number, s.id AS session_id
                  FROM committee_session s
                  JOIN attendance a ON a.session_id = s.id AND a.user_id = ? AND a.status = 'PRESENT'
                  JOIN agenda_item ai ON ai.session_id = s.id AND ai.file_id IS NOT NULL AND ai.status = 'PENDING'
                  JOIN proc_file f ON f.id = ai.file_id
                 WHERE s.status = 'IN_PROGRESS'
                   AND NOT EXISTS (SELECT 1 FROM coi_declaration d WHERE d.agenda_item_id = ai.id AND d.user_id = ?)
                 ORDER BY s.scheduled_at, ai.position
                """, me, me));
        out.put("myPendingVotes", q("""
                SELECT d.id AS decision_id, f.file_number, f.title, s.session_number
                  FROM decision d
                  JOIN committee_session s ON s.id = d.session_id
                  JOIN proc_file f ON f.id = d.file_id
                  JOIN attendance a ON a.session_id = s.id AND a.user_id = ? AND a.status = 'PRESENT'
                 WHERE d.status = 'VOTING'
                   AND NOT EXISTS (SELECT 1 FROM vote v WHERE v.decision_id = d.id AND v.user_id = ?)
                   AND NOT EXISTS (SELECT 1 FROM coi_declaration c
                                    WHERE c.agenda_item_id = d.agenda_item_id AND c.user_id = ? AND c.has_interest)
                """, me, me, me));
        out.put("myAttendance", q("""
                SELECT c.code AS committee_code, c.name AS committee_name,
                       count(s.id) AS sessions_held,
                       count(a.id) FILTER (WHERE a.status = 'PRESENT') AS present,
                       count(a.id) FILTER (WHERE a.status = 'ABSENT_WITH_PERMISSION') AS absent_with_permission,
                       count(a.id) FILTER (WHERE a.status = 'ON_LEAVE') AS on_leave,
                       count(a.id) FILTER (WHERE a.status = 'ABSENT_UNJUSTIFIED') AS absent_unjustified,
                       CASE WHEN count(s.id) = 0 THEN NULL
                            ELSE round(100.0 * count(a.id) FILTER (WHERE a.status = 'PRESENT') / count(s.id), 1) END AS rate_pct
                  FROM committee_membership m
                  JOIN committee c ON c.id = m.committee_id
                  LEFT JOIN committee_session s ON s.committee_id = m.committee_id AND s.status = 'HELD'
                       AND s.scheduled_at::date BETWEEN m.term_start AND m.term_end
                  LEFT JOIN attendance a ON a.session_id = s.id AND a.membership_id = m.id
                 WHERE m.user_id = ? AND m.status = 'ACTIVE'
                 GROUP BY c.code, c.name ORDER BY c.code
                """, me));
        out.put("myTasks", q("""
                SELECT id, kind, title, due_date, status FROM tracked_task
                 WHERE assignee_id = ? AND status = 'OPEN' ORDER BY due_date NULLS LAST
                """, me));
        return out;
    }

    // ------------------------------------------------------------- chairperson

    public Map<String, Object> chair(UUID committeeId) {
        ConfigSnapshot cfg = config.snapshot();
        LocalDate today = Time.today();
        Date todayDate = Date.valueOf(today);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("committee", Rows.camel(jdbc.queryForMap(
                "SELECT id, name, code, size FROM committee WHERE id = ?", committeeId)));
        out.put("workloadPerOfficer", q("""
                SELECT coalesce(u.full_name, '(unassigned)') AS officer_name, f.procurement_officer_id AS officer_id,
                       count(*) AS open_files,
                       count(*) FILTER (WHERE f.target_date < ?) AS overdue_files
                  FROM proc_file f LEFT JOIN app_user u ON u.id = f.procurement_officer_id
                 WHERE f.committee_id = ? AND f.closed_at IS NULL
                 GROUP BY u.full_name, f.procurement_officer_id ORDER BY open_files DESC
                """, todayDate, committeeId));
        out.put("overdueByStageAndType", q("""
                SELECT f.stage, f.item_type, count(*) AS overdue
                  FROM proc_file f
                 WHERE f.committee_id = ? AND f.closed_at IS NULL AND f.target_date < ?
                 GROUP BY f.stage, f.item_type ORDER BY f.stage, f.item_type
                """, committeeId, todayDate));
        out.put("overdueFiles", q("""
                SELECT id, file_number, item_type, title, stage, target_date, ? - target_date AS days_overdue
                  FROM proc_file WHERE committee_id = ? AND closed_at IS NULL AND target_date < ?
                 ORDER BY target_date LIMIT 50
                """, todayDate, committeeId, todayDate));
        out.put("quorumRisk", quorumRisk(committeeId, cfg));
        out.put("turnaroundByType", q("""
                SELECT f.item_type, count(*) AS files,
                       round(avg((d.issued_at AT TIME ZONE 'Africa/Kigali')::date - f.date_received), 1) AS avg_days,
                       max((d.issued_at AT TIME ZONE 'Africa/Kigali')::date - f.date_received) AS max_days
                  FROM proc_file f JOIN decision d ON d.file_id = f.id AND d.issued_at IS NOT NULL
                 WHERE f.committee_id = ?
                 GROUP BY f.item_type ORDER BY f.item_type
                """, committeeId));
        int lead = cfg.getInt(ConfigKeys.BID_VALIDITY_REGULATOR_LEAD_DAYS);
        out.put("bidValidityExpiringSoon", q("""
                SELECT id, file_number, title, tender_ref, bid_validity_expiry,
                       bid_validity_expiry - ? AS regulator_request_deadline,
                       bid_validity_expiry - ?::date AS days_to_expiry
                  FROM proc_file
                 WHERE committee_id = ? AND item_type = 'A' AND closed_at IS NULL
                   AND bid_validity_expiry IS NOT NULL AND bid_validity_expiry <= ?
                 ORDER BY bid_validity_expiry
                """, lead, todayDate, committeeId, Date.valueOf(today.plusDays(lead + 30L))));
        out.put("contractsNearCeiling", contractsNearCeiling(committeeId, cfg));
        return out;
    }

    private List<Map<String, Object>> quorumRisk(UUID committeeId, ConfigSnapshot cfg) {
        ConfigSnapshot.Fraction current = cfg.getFraction(ConfigKeys.QUORUM_RULE);
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> s : jdbc.queryForList("""
                SELECT s.id, s.session_number, s.scheduled_at, s.committee_size,
                       s.config_snapshot -> 'values' -> 'quorum.rule' ->> 'numerator' AS num,
                       s.config_snapshot -> 'values' -> 'quorum.rule' ->> 'denominator' AS den,
                       (SELECT count(*) FROM committee_membership m
                         WHERE m.committee_id = s.committee_id AND m.status = 'ACTIVE') AS active_members,
                       (SELECT count(*) FROM absence_apology ap WHERE ap.session_id = s.id) AS apologies
                  FROM committee_session s
                 WHERE s.committee_id = ? AND s.status = 'PLANNED' AND s.scheduled_at > now()
                 ORDER BY s.scheduled_at LIMIT 10
                """, committeeId)) {
            int size = (Integer) s.get("committee_size");
            int required = s.get("num") != null
                    ? (int) Math.ceil((double) size * Integer.parseInt((String) s.get("num")) / Integer.parseInt((String) s.get("den")))
                    : current.ceilOf(size);
            long expected = ((Number) s.get("active_members")).longValue() - ((Number) s.get("apologies")).longValue();
            Map<String, Object> row = Rows.camel(s);
            row.remove("num");
            row.remove("den");
            row.put("required", required);
            row.put("expectedPresent", expected);
            row.put("risk", expected < required ? "NOT_MET" : expected == required ? "EXACTLY_AT_QUORUM" : "OK");
            out.add(row);
        }
        return out;
    }

    private List<Map<String, Object>> contractsNearCeiling(UUID committeeId, ConfigSnapshot cfg) {
        BigDecimal warning = cfg.getDecimal(ConfigKeys.AMENDMENT_WARNING_PCT);
        BigDecimal ceiling = cfg.getDecimal(ConfigKeys.AMENDMENT_CEILING_PCT);
        List<Map<String, Object>> rows = q("""
                SELECT c.id, c.contract_ref, c.title, c.currency, c.original_value,
                       coalesce(sum(a.approved_value) FILTER (WHERE a.status = 'APPROVED'), 0) AS approved_cumulative,
                       coalesce(sum(a.requested_value) FILTER (WHERE a.status IN ('OPEN', 'RECOMMENDED')), 0) AS pending
                  FROM contract c JOIN amendment_request a ON a.contract_id = c.id
                 WHERE (CAST(? AS uuid) IS NULL
                        OR c.id IN (SELECT contract_id FROM proc_file WHERE committee_id = ? AND contract_id IS NOT NULL))
                 GROUP BY c.id, c.contract_ref, c.title, c.currency, c.original_value
                """, committeeId, committeeId);
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> r : rows) {
            BigDecimal original = (BigDecimal) r.get("originalValue");
            BigDecimal approvedPct = pct((BigDecimal) r.get("approvedCumulative"), original);
            BigDecimal withPendingPct = pct(((BigDecimal) r.get("approvedCumulative")).add((BigDecimal) r.get("pending")), original);
            if (withPendingPct.compareTo(warning) >= 0) {
                r.put("approvedPct", approvedPct);
                r.put("withPendingPct", withPendingPct);
                r.put("ceilingPct", ceiling);
                r.put("level", withPendingPct.compareTo(ceiling) > 0 ? "EXCEEDED" : "APPROACHING");
                out.add(r);
            }
        }
        return out;
    }

    // --------------------------------------------------------------------- CBM

    public Map<String, Object> cbm() {
        ConfigSnapshot cfg = config.snapshot();
        LocalDate today = Time.today();
        Map<String, Object> out = new LinkedHashMap<>();
        List<Map<String, Object>> committees = q("""
                SELECT c.id, c.name, c.code, c.size, c.status, c.regulator_auth_ref,
                       count(m.id) FILTER (WHERE m.status = 'ACTIVE') AS active_members,
                       count(m.id) FILTER (WHERE m.status = 'ACTIVE' AND u.is_procurement_officer) AS procurement_officers,
                       bool_or(m.role = 'CHAIRPERSON' AND m.status = 'ACTIVE') AS has_chair,
                       bool_or(m.role = 'VICE_CHAIRPERSON' AND m.status = 'ACTIVE') AS has_vice_chair,
                       bool_or(m.role = 'SECRETARY' AND m.status = 'ACTIVE') AS has_secretary,
                       (SELECT count(*) FROM proc_file f WHERE f.committee_id = c.id AND f.closed_at IS NULL) AS open_files,
                       (SELECT count(*) FROM proc_file f WHERE f.committee_id = c.id AND f.closed_at IS NULL
                           AND f.target_date < ?) AS overdue_files
                  FROM committee c
                  LEFT JOIN committee_membership m ON m.committee_id = c.id
                  LEFT JOIN app_user u ON u.id = m.user_id
                 WHERE c.status = 'ACTIVE'
                 GROUP BY c.id ORDER BY c.code
                """, Date.valueOf(today));
        int activeCommittees = committees.size();
        for (Map<String, Object> c : committees) {
            List<String> issues = new ArrayList<>();
            long active = ((Number) c.get("activeMembers")).longValue();
            long pos = ((Number) c.get("procurementOfficers")).longValue();
            if (active < (Integer) c.get("size")) issues.add("composition.below_size");
            if (!Boolean.TRUE.equals(c.get("hasChair"))) issues.add("composition.no_chairperson");
            if (!Boolean.TRUE.equals(c.get("hasViceChair"))) issues.add("composition.no_vice_chairperson");
            if (!Boolean.TRUE.equals(c.get("hasSecretary"))) issues.add("composition.no_secretary");
            if (pos == 0) issues.add("composition.no_procurement_officer");
            if (active > 0 && pos * 2 > active) issues.add("composition.procurement_officer_majority");
            if (activeCommittees > 1 && c.get("regulatorAuthRef") == null) issues.add("committee.regulator_authorisation_required");
            c.put("complianceIssues", issues);
            c.put("compliant", issues.isEmpty());
        }
        out.put("committees", committees);
        out.put("pendingRecommendations", q("""
                SELECT d.id AS decision_id, d.item_type, d.recommendation_code, d.recommendation_text, d.issued_at,
                       f.id AS file_id, f.file_number, f.title, s.session_number, c.code AS committee_code,
                       (now()::date - (d.issued_at AT TIME ZONE 'Africa/Kigali')::date) AS days_waiting
                  FROM decision d
                  JOIN proc_file f ON f.id = d.file_id
                  JOIN committee_session s ON s.id = d.session_id
                  JOIN committee c ON c.id = s.committee_id
                 WHERE d.status = 'ISSUED'
                   AND NOT EXISTS (SELECT 1 FROM cbm_response r WHERE r.decision_id = d.id
                                    AND r.responded_at >= d.issued_at)
                 ORDER BY d.issued_at
                """));
        out.put("termExpiries", q("""
                SELECT m.id AS membership_id, u.full_name, m.role, m.term_end, c.code AS committee_code,
                       m.consecutive_term, m.term_end - ?::date AS days_left
                  FROM committee_membership m
                  JOIN app_user u ON u.id = m.user_id
                  JOIN committee c ON c.id = m.committee_id
                 WHERE m.status = 'ACTIVE' AND m.term_end <= ?
                 ORDER BY m.term_end
                """, Date.valueOf(today), Date.valueOf(today.plusDays(cfg.getInt(ConfigKeys.TERM_EXPIRY_ALERT_DAYS)))));
        out.put("workloadByCommittee", q("""
                SELECT c.code AS committee_code, f.item_type, count(*) AS open_files
                  FROM proc_file f JOIN committee c ON c.id = f.committee_id
                 WHERE f.closed_at IS NULL
                 GROUP BY c.code, f.item_type ORDER BY c.code, f.item_type
                """));
        out.put("openTasks", q("""
                SELECT t.id, t.kind, t.title, t.due_date, c.code AS committee_code, t.due_date < ? AS overdue
                  FROM tracked_task t LEFT JOIN committee c ON c.id = t.committee_id
                 WHERE t.status = 'OPEN' ORDER BY t.due_date NULLS LAST
                """, Date.valueOf(today)));
        out.put("contractsNearCeiling", contractsNearCeiling(null, cfg));
        return out;
    }

    // ----------------------------------------------------------------- reports

    public Map<String, Object> summary(LocalDate from, LocalDate to, UUID committeeId) {
        Date f = Date.valueOf(from);
        Date t = Date.valueOf(to);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("from", from);
        out.put("to", to);
        out.put("filesByType", q("""
                SELECT ty.code AS item_type,
                       (SELECT count(*) FROM proc_file x WHERE x.item_type = ty.code
                           AND x.date_received BETWEEN ? AND ? AND (CAST(? AS uuid) IS NULL OR x.committee_id = ?)) AS received,
                       (SELECT count(*) FROM decision d JOIN proc_file x ON x.id = d.file_id WHERE d.item_type = ty.code
                           AND (d.issued_at AT TIME ZONE 'Africa/Kigali')::date BETWEEN ? AND ?
                           AND (CAST(? AS uuid) IS NULL OR x.committee_id = ?)) AS recommendations_issued,
                       (SELECT count(*) FROM proc_file x WHERE x.item_type = ty.code
                           AND (x.closed_at AT TIME ZONE 'Africa/Kigali')::date BETWEEN ? AND ?
                           AND (CAST(? AS uuid) IS NULL OR x.committee_id = ?)) AS closed
                  FROM file_type ty ORDER BY ty.code
                """, f, t, committeeId, committeeId, f, t, committeeId, committeeId, f, t, committeeId, committeeId));
        out.put("sessions", q("""
                SELECT c.code AS committee_code,
                       count(s.id) FILTER (WHERE s.status = 'HELD') AS held,
                       count(s.id) FILTER (WHERE s.status = 'CANCELLED') AS cancelled,
                       count(s.id) FILTER (WHERE s.kind = 'EXTRAORDINARY' AND s.status = 'HELD') AS extraordinary_held,
                       count(s.id) FILTER (WHERE s.short_notice AND s.status = 'HELD') AS short_notice_held
                  FROM committee c
                  LEFT JOIN committee_session s ON s.committee_id = c.id
                       AND (s.scheduled_at AT TIME ZONE 'Africa/Kigali')::date BETWEEN ? AND ?
                 WHERE (CAST(? AS uuid) IS NULL OR c.id = ?)
                 GROUP BY c.code ORDER BY c.code
                """, f, t, committeeId, committeeId));
        out.put("attendance", q("""
                SELECT u.full_name, c.code AS committee_code, m.role,
                       count(s.id) AS sessions_held,
                       count(a.id) FILTER (WHERE a.status = 'PRESENT') AS present,
                       count(a.id) FILTER (WHERE a.status = 'ABSENT_WITH_PERMISSION') AS absent_with_permission,
                       count(a.id) FILTER (WHERE a.status = 'ON_LEAVE') AS on_leave,
                       count(a.id) FILTER (WHERE a.status = 'ABSENT_UNJUSTIFIED') AS absent_unjustified,
                       CASE WHEN count(s.id) = 0 THEN NULL
                            ELSE round(100.0 * count(a.id) FILTER (WHERE a.status = 'PRESENT') / count(s.id), 1) END AS rate_pct
                  FROM committee_membership m
                  JOIN app_user u ON u.id = m.user_id
                  JOIN committee c ON c.id = m.committee_id
                  JOIN committee_session s ON s.committee_id = m.committee_id AND s.status = 'HELD'
                       AND (s.scheduled_at AT TIME ZONE 'Africa/Kigali')::date BETWEEN greatest(?, m.term_start) AND least(?, m.term_end)
                  LEFT JOIN attendance a ON a.session_id = s.id AND a.membership_id = m.id
                 WHERE (CAST(? AS uuid) IS NULL OR m.committee_id = ?)
                 GROUP BY u.full_name, c.code, m.role ORDER BY c.code, u.full_name
                """, f, t, committeeId, committeeId));
        return out;
    }

    // ----------------------------------------------------------------- helpers

    public boolean committeeVisible(UUID committeeId) {
        Integer n = jdbc.queryForObject("SELECT count(*) FROM committee WHERE id = ?", Integer.class, committeeId);
        return n != null && n > 0;
    }

    private List<Map<String, Object>> q(String sql, Object... args) {
        return new ArrayList<>(Rows.camel(jdbc.queryForList(sql, args)));
    }

    private static BigDecimal pct(BigDecimal part, BigDecimal whole) {
        return part.multiply(BigDecimal.valueOf(100)).divide(whole, 2, java.math.RoundingMode.HALF_UP);
    }
}
