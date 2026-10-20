package rw.gov.ptc.session;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import rw.gov.ptc.audit.AuditService;
import rw.gov.ptc.calendar.WorkingDayCalendar;
import rw.gov.ptc.committee.Committee;
import rw.gov.ptc.committee.CommitteeRepository;
import rw.gov.ptc.common.ApiException;
import rw.gov.ptc.common.Rows;
import rw.gov.ptc.common.Time;
import rw.gov.ptc.config.ConfigKeys;
import rw.gov.ptc.config.ConfigService;
import rw.gov.ptc.config.ConfigSnapshot;
import rw.gov.ptc.file.Numbering;
import rw.gov.ptc.security.Access;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Session scheduling. Only the Chairperson, Vice-Chairperson or Secretary
 * of the committee may create or modify a session; the CBM cannot.
 */
@Service
public class SessionService {

    private static final Set<String> KINDS = Set.of("ORDINARY", "EXTRAORDINARY");

    public record SessionRequest(UUID committeeId, String kind, Instant scheduledAt, String location,
                                 String shortNoticeReason, Long version) {
    }

    private final CommitteeSessionRepository sessions;
    private final CommitteeRepository committees;
    private final SessionAccess sessionAccess;
    private final Numbering numbering;
    private final ConfigService config;
    private final WorkingDayCalendar calendar;
    private final AuditService audit;
    private final Access access;
    private final JdbcTemplate jdbc;

    public SessionService(CommitteeSessionRepository sessions, CommitteeRepository committees,
                          SessionAccess sessionAccess, Numbering numbering, ConfigService config,
                          WorkingDayCalendar calendar, AuditService audit, Access access, JdbcTemplate jdbc) {
        this.sessions = sessions;
        this.committees = committees;
        this.sessionAccess = sessionAccess;
        this.numbering = numbering;
        this.config = config;
        this.calendar = calendar;
        this.audit = audit;
        this.access = access;
        this.jdbc = jdbc;
    }

    public List<Map<String, Object>> list(UUID committeeId, LocalDate from, LocalDate to) {
        StringBuilder sql = new StringBuilder("""
                SELECT s.id, s.committee_id, c.code AS committee_code, c.name AS committee_name, s.session_number,
                       s.kind, s.scheduled_at, s.location, s.status, s.short_notice,
                       (SELECT count(*) FROM agenda_item a WHERE a.session_id = s.id) AS agenda_count
                  FROM committee_session s JOIN committee c ON c.id = s.committee_id WHERE 1 = 1
                """);
        List<Object> args = new ArrayList<>();
        if (!access.canReadAllCommittees()) {
            List<UUID> mine = access.principal().committees().stream().map(g -> g.committeeId()).toList();
            if (mine.isEmpty()) {
                return List.of();
            }
            sql.append(" AND s.committee_id = ANY(?)");
            args.add(mine.toArray(new UUID[0]));
        }
        if (committeeId != null) { sql.append(" AND s.committee_id = ?"); args.add(committeeId); }
        if (from != null) { sql.append(" AND s.scheduled_at >= ?"); args.add(java.sql.Timestamp.from(Time.startOfDay(from))); }
        if (to != null) { sql.append(" AND s.scheduled_at < ?"); args.add(java.sql.Timestamp.from(Time.startOfDay(to.plusDays(1)))); }
        sql.append(" ORDER BY s.scheduled_at DESC");
        return Rows.camel(jdbc.queryForList(sql.toString(), args.toArray()));
    }

    /** The earliest start that respects the notice period if convened now. */
    Instant earliestWithoutShortNotice(String kind, Instant convenedAt, ConfigSnapshot cfg) {
        if ("EXTRAORDINARY".equals(kind)) {
            return convenedAt.plus(Duration.ofHours(cfg.getInt(ConfigKeys.NOTICE_EXTRAORDINARY_HOURS)));
        }
        LocalDate day = calendar.addWorkingDays(Time.localDate(convenedAt), cfg.getInt(ConfigKeys.NOTICE_ORDINARY_WORKING_DAYS));
        return Time.startOfDay(day);
    }

    private void applyNotice(CommitteeSession s, String reason, ConfigSnapshot cfg) {
        Instant earliest = earliestWithoutShortNotice(s.kind, s.convenedAt, cfg);
        s.shortNotice = s.scheduledAt.isBefore(earliest);
        if (s.shortNotice) {
            if (reason == null || reason.isBlank()) {
                throw ApiException.rule("session.short_notice_reason_required",
                        "This session is inside the notice period: record why short notice was given",
                        Map.of("earliestWithoutShortNotice", earliest.toString()));
            }
            s.shortNoticeReason = reason.trim();
        } else {
            s.shortNoticeReason = null;
        }
    }

    @Transactional
    public CommitteeSession create(SessionRequest req) {
        if (req.committeeId() == null) {
            throw ApiException.badRequest("session.committee_required", "Choose the committee");
        }
        access.requireOfficer(req.committeeId());
        Committee c = committees.findById(req.committeeId()).orElseThrow(() -> ApiException.notFound("Committee"));
        if (!"ACTIVE".equals(c.status)) {
            throw ApiException.rule("committee.inactive", "The committee is dissolved");
        }
        if (req.kind() == null || !KINDS.contains(req.kind()) || req.scheduledAt() == null) {
            throw ApiException.badRequest("session.invalid", "Kind and date/time are required");
        }
        ConfigSnapshot cfg = config.snapshot();
        CommitteeSession s = new CommitteeSession();
        s.committeeId = c.id;
        s.kind = req.kind();
        s.scheduledAt = req.scheduledAt();
        s.location = req.location();
        s.convenedAt = Instant.now();
        applyNotice(s, req.shortNoticeReason(), cfg);
        int year = Time.localDate(s.scheduledAt).getYear();
        s.sessionNumber = "%s %d/%03d".formatted(c.code, year, numbering.next("SESSION:" + c.id + ":" + year));
        s.committeeSize = c.size;
        s.configSnapshot = cfg.json();
        s.createdBy = access.userId();
        sessions.saveAndFlush(s);
        audit.created("committee_session", s.id, s);
        return s;
    }

    @Transactional
    public CommitteeSession update(UUID id, SessionRequest req) {
        CommitteeSession s = sessionAccess.forOfficer(id);
        if (!"PLANNED".equals(s.status)) {
            throw ApiException.rule("session.not_planned", "Only planned sessions can be rescheduled");
        }
        if (req.version() != null && !req.version().equals(s.version)) {
            throw ApiException.conflict("concurrent_modification", "Reload and try again");
        }
        var before = audit.snapshot(s);
        boolean reschedule = false;
        if (req.kind() != null) {
            if (!KINDS.contains(req.kind())) {
                throw ApiException.badRequest("session.invalid", "Unknown kind");
            }
            reschedule |= !req.kind().equals(s.kind);
            s.kind = req.kind();
        }
        if (req.scheduledAt() != null && !req.scheduledAt().equals(s.scheduledAt)) {
            s.scheduledAt = req.scheduledAt();
            reschedule = true;
        }
        if (req.location() != null) s.location = req.location();
        if (reschedule) {
            // Rescheduling re-convenes the members: notice runs from now.
            s.convenedAt = Instant.now();
            applyNotice(s, req.shortNoticeReason() != null ? req.shortNoticeReason() : s.shortNoticeReason,
                    config.fromStored(s.configSnapshot));
        } else if (req.shortNoticeReason() != null && s.shortNotice) {
            s.shortNoticeReason = req.shortNoticeReason();
        }
        sessions.saveAndFlush(s);
        audit.updated("committee_session", s.id, before, s);
        return s;
    }

    @Transactional
    public CommitteeSession changeStatus(UUID id, String status) {
        CommitteeSession s = sessionAccess.forOfficer(id);
        var before = audit.snapshot(s);
        switch (status == null ? "" : status) {
            case "IN_PROGRESS" -> {
                if (!"PLANNED".equals(s.status)) throw invalidTransition(s, status);
                s.openedAt = Instant.now();
            }
            case "HELD" -> {
                if (!"IN_PROGRESS".equals(s.status) && !"PLANNED".equals(s.status)) throw invalidTransition(s, status);
                if (s.openedAt == null) s.openedAt = Instant.now();
                s.closedAt = Instant.now();
            }
            case "CANCELLED" -> {
                if (!"PLANNED".equals(s.status)) throw invalidTransition(s, status);
            }
            default -> throw ApiException.badRequest("session.invalid_status", "Unknown status");
        }
        s.status = status;
        sessions.saveAndFlush(s);
        audit.record("STATUS_CHANGE", "committee_session", s.id, before, s);
        return s;
    }

    private static ApiException invalidTransition(CommitteeSession s, String to) {
        return ApiException.rule("session.invalid_transition", "A " + s.status.toLowerCase()
                + " session cannot become " + to.toLowerCase(), Map.of("from", s.status, "to", to));
    }

    // ----------------------------------------------------------------- view

    public Map<String, Object> view(UUID id) {
        CommitteeSession s = sessionAccess.readable(id);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("session", s);
        out.put("committee", Rows.camel(jdbc.queryForMap("SELECT id, name, code, size FROM committee WHERE id = ?", s.committeeId)));
        out.put("agenda", Rows.camel(jdbc.queryForList("""
                SELECT a.id, a.position, a.item_type, a.file_id, p.file_number, p.title, p.tender_ref, a.admin_kind,
                       a.admin_note, a.status, d.id AS decision_id, d.status AS decision_status
                  FROM agenda_item a LEFT JOIN proc_file p ON p.id = a.file_id
                  LEFT JOIN decision d ON d.agenda_item_id = a.id
                 WHERE a.session_id = ? ORDER BY a.position
                """, id)));
        List<Map<String, Object>> members = members(s);
        out.put("members", members);
        out.put("quorum", sessionAccess.quorum(s));
        out.put("myRole", access.committeeRole(s.committeeId).map(Enum::name).orElse(null));
        out.put("myApology", members.stream().filter(m -> access.userId().equals(m.get("userId")))
                .findFirst().map(m -> m.get("apology")).orElse(null));
        return out;
    }

    /** Members holding a seat on the session date, with attendance and apology. */
    public List<Map<String, Object>> members(CommitteeSession s) {
        LocalDate day = Time.localDate(s.scheduledAt);
        List<Map<String, Object>> rows = Rows.camel(jdbc.queryForList("""
                SELECT m.id AS membership_id, m.user_id, u.full_name, m.role,
                       a.status AS attendance_status, a.version AS attendance_version, a.recorded_at,
                       a.client_recorded_at, ap.kind AS apology_kind, ap.reason AS apology_reason,
                       ap.timely AS apology_timely, ap.submitted_at AS apology_submitted_at
                  FROM committee_membership m JOIN app_user u ON u.id = m.user_id
                  LEFT JOIN attendance a ON a.session_id = ? AND a.membership_id = m.id
                  LEFT JOIN absence_apology ap ON ap.session_id = ? AND ap.user_id = m.user_id
                 WHERE m.committee_id = ? AND m.term_start <= ? AND m.term_end >= ?
                   AND (m.status = 'ACTIVE' OR m.ended_at > ?)
                 ORDER BY CASE m.role WHEN 'CHAIRPERSON' THEN 1 WHEN 'VICE_CHAIRPERSON' THEN 2
                                      WHEN 'SECRETARY' THEN 3 ELSE 4 END, u.full_name
                """, s.id, s.id, s.committeeId, java.sql.Date.valueOf(day), java.sql.Date.valueOf(day),
                java.sql.Timestamp.from(s.scheduledAt)));
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> r : rows) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("membershipId", r.get("membershipId"));
            m.put("userId", r.get("userId"));
            m.put("fullName", r.get("fullName"));
            m.put("role", r.get("role"));
            Object status = r.get("attendanceStatus");
            m.put("attendance", status == null ? null : Map.of("status", status,
                    "version", r.get("attendanceVersion"), "recordedAt", r.get("recordedAt")));
            Object kind = r.get("apologyKind");
            Map<String, Object> apology = null;
            if (kind != null) {
                apology = new LinkedHashMap<>();
                apology.put("kind", kind);
                apology.put("reason", r.get("apologyReason"));
                apology.put("timely", r.get("apologyTimely"));
                apology.put("submittedAt", r.get("apologySubmittedAt"));
            }
            m.put("apology", apology);
            // Absent with no apology to the Chairperson and Secretary beforehand.
            boolean absent = status != null && !"PRESENT".equals(status);
            m.put("unnotifiedAbsence", absent && (apology == null || !Boolean.TRUE.equals(apology.get("timely"))));
            out.add(m);
        }
        return out;
    }
}
