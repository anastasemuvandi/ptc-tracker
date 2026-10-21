package rw.gov.ptc.reminder.service.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import rw.gov.ptc.audit.service.AuditService;
import rw.gov.ptc.calendar.service.WorkingDayCalendar;
import rw.gov.ptc.common.Ids;
import rw.gov.ptc.common.Time;
import rw.gov.ptc.config.component.ConfigKeys;
import rw.gov.ptc.config.dto.ConfigSnapshot;
import rw.gov.ptc.config.service.ConfigService;
import rw.gov.ptc.notification.service.NotificationService;
import rw.gov.ptc.reminder.service.ReminderScanner;
import rw.gov.ptc.tenancy.TenantContext;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class ReminderScannerImpl implements ReminderScanner {

    private static final Logger log = LoggerFactory.getLogger(ReminderScannerImpl.class);
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

    private final JdbcTemplate jdbc;
    private final ConfigService config;
    private final WorkingDayCalendar calendar;
    private final NotificationService notifications;
    private final AuditService audit;

    public ReminderScannerImpl(JdbcTemplate jdbc, ConfigService config, WorkingDayCalendar calendar,
                           NotificationService notifications, AuditService audit) {
        this.jdbc = jdbc;
        this.config = config;
        this.calendar = calendar;
        this.notifications = notifications;
        this.audit = audit;
    }

    @Override
    public void scanAll() {
        for (UUID institution : jdbc.queryForList("SELECT * FROM app_active_institution_ids()", UUID.class)) {
            try {
                TenantContext.runAs(new TenantContext.Scope(institution, null, false), () -> scanInstitution(Time.today()));
            } catch (RuntimeException e) {
                log.error("Reminder scan failed for institution {}", institution, e);
            }
        }
    }

    @Override
    public void scanInstitution(LocalDate today) {
        ConfigSnapshot cfg = config.snapshot();
        cureDeadlines(today);
        bidValidity(today, cfg);
        escalatedAmendments(today, cfg);
        contracts(today, cfg);
        termExpiry(today, cfg);
        tasks(today);
        upcomingSessions();
        pendingDeclarations();
        minutesAwaitingAcknowledgement(today);
    }

    // --------------------------------------------------------------- type A

    void cureDeadlines(LocalDate today) {
        for (Map<String, Object> r : jdbc.queryForList("""
                SELECT r.id, r.file_id, r.bidder_ref, r.deadline, f.file_number, f.committee_id, f.procurement_officer_id
                  FROM admin_cure_request r JOIN proc_file f ON f.id = r.file_id
                 WHERE r.outcome = 'PENDING' AND f.closed_at IS NULL
                """)) {
            LocalDate deadline = ((Date) r.get("deadline")).toLocalDate();
            UUID id = (UUID) r.get("id");
            List<String> args = List.of((String) r.get("file_number"), (String) r.get("bidder_ref"), iso(deadline));
            Set<UUID> to = fileTeam((UUID) r.get("committee_id"), (UUID) r.get("procurement_officer_id"));
            if (today.isAfter(deadline)) {
                send(to, "CURE_OVERDUE", args, "/n/cure/" + id + "?fileId=" + r.get("file_id"), "admin_cure_request", id, "cure:" + id + ":overdue:" + deadline);
            } else if (calendar.workingDaysBetween(today, deadline) <= 2) {
                send(to, "CURE_DUE_SOON", args, "/n/cure/" + id + "?fileId=" + r.get("file_id"), "admin_cure_request", id, "cure:" + id + ":due:" + deadline);
            }
        }
    }

    void bidValidity(LocalDate today, ConfigSnapshot cfg) {
        int lead = cfg.getInt(ConfigKeys.BID_VALIDITY_REGULATOR_LEAD_DAYS);
        int beforeRequest = cfg.getInt(ConfigKeys.BID_VALIDITY_ALERT_BEFORE_REQUEST_DAYS);
        int beforeExpiry = cfg.getInt(ConfigKeys.BID_VALIDITY_ALERT_BEFORE_EXPIRY_DAYS);
        for (Map<String, Object> f : jdbc.queryForList("""
                SELECT id, file_number, tender_ref, committee_id, procurement_officer_id, bid_validity_expiry
                  FROM proc_file
                 WHERE item_type = 'A' AND closed_at IS NULL AND bid_validity_expiry IS NOT NULL
                """)) {
            UUID fileId = (UUID) f.get("id");
            LocalDate expiry = ((Date) f.get("bid_validity_expiry")).toLocalDate();
            LocalDate requestBy = expiry.minusDays(lead);
            List<String> args = List.of((String) f.get("file_number"), nz((String) f.get("tender_ref")), iso(expiry), iso(requestBy));
            Set<UUID> to = fileTeam((UUID) f.get("committee_id"), (UUID) f.get("procurement_officer_id"));
            String link = "/n/bid-validity/" + fileId;
            String key = "bidvalidity:" + fileId + ":" + expiry;
            if (today.isAfter(expiry)) {
                to.addAll(cbms());
                send(to, "BID_VALIDITY_EXPIRED", args, link, "proc_file", fileId, key + ":expired");
                continue;
            }
            if (!today.isBefore(expiry.minusDays(beforeExpiry))) {
                send(to, "BID_VALIDITY_EXPIRING", args, link, "proc_file", fileId, key + ":expiring");
            }
            if (!today.isBefore(requestBy)) {
                send(to, "BID_VALIDITY_REQUEST_OVERDUE", args, link, "proc_file", fileId, key + ":request-overdue");
            } else if (!today.isBefore(requestBy.minusDays(beforeRequest))) {
                send(to, "BID_VALIDITY_REQUEST_DUE", args, link, "proc_file", fileId, key + ":request-due");
            }
            if (!today.isBefore(requestBy.minusDays(beforeRequest))) {
                ensureTask((UUID) f.get("committee_id"), "BID_VALIDITY_EXTENSION_REQUEST",
                        "Send bid validity extension request to the regulator — file " + f.get("file_number"),
                        "Bids expire on " + iso(expiry) + "; the request must reach the regulator by " + iso(requestBy) + ".",
                        requestBy, "proc_file", fileId, (UUID) f.get("procurement_officer_id"));
            }
        }
    }

    // ------------------------------------------------------------ type C/D

    /** Lead time for amendments that must be escalated (cumulative figure over a ceiling). */
    void escalatedAmendments(LocalDate today, ConfigSnapshot cfg) {
        BigDecimal ceilingPct = cfg.getDecimal(ConfigKeys.AMENDMENT_CEILING_PCT);
        var absolute = cfg.get(ConfigKeys.AMENDMENT_ABSOLUTE_CEILINGS);
        int leadDays = cfg.getInt(ConfigKeys.AMENDMENT_ESCALATION_LEAD_DAYS);
        for (Map<String, Object> a : jdbc.queryForList("""
                SELECT ar.id, ar.contract_id, ar.requested_value, ar.currency, f.id AS file_id, f.file_number, f.committee_id,
                       f.procurement_officer_id, c.contract_ref, c.original_value, c.current_completion_date,
                       (SELECT coalesce(sum(o.approved_value), 0) FROM amendment_request o
                         WHERE o.contract_id = ar.contract_id AND o.status = 'APPROVED' AND o.id <> ar.id) AS prior
                  FROM amendment_request ar
                  JOIN proc_file f ON f.id = ar.file_id
                  JOIN contract c ON c.id = ar.contract_id
                 WHERE ar.status IN ('OPEN', 'RECOMMENDED') AND f.closed_at IS NULL
                """)) {
            BigDecimal resulting = ((BigDecimal) a.get("prior")).add((BigDecimal) a.get("requested_value"));
            BigDecimal pct = resulting.multiply(BigDecimal.valueOf(100))
                    .divide((BigDecimal) a.get("original_value"), 2, RoundingMode.HALF_UP);
            String currency = ((String) a.get("currency")).trim();
            boolean overAbs = absolute.has(currency) && resulting.compareTo(new BigDecimal(absolute.get(currency).asText())) > 0;
            if (pct.compareTo(ceilingPct) <= 0 && !overAbs) {
                continue;
            }
            LocalDate completion = ((Date) a.get("current_completion_date")).toLocalDate();
            LocalDate submitBy = completion.minusDays(leadDays);
            if (today.isBefore(submitBy.minusDays(14))) {
                continue;
            }
            UUID fileId = (UUID) a.get("file_id");
            Set<UUID> to = fileTeam((UUID) a.get("committee_id"), (UUID) a.get("procurement_officer_id"));
            to.addAll(cbms());
            send(to, "AMENDMENT_ESCALATION_LEAD", List.of((String) a.get("file_number"), (String) a.get("contract_ref"),
                            iso(submitBy), iso(completion)), "/n/contract/" + a.get("contract_id"), "proc_file", fileId,
                    "amendment:" + a.get("id") + ":lead:" + submitBy + (today.isAfter(submitBy) ? ":late" : ""));
        }
    }

    void contracts(LocalDate today, ConfigSnapshot cfg) {
        int completionDays = cfg.getInt(ConfigKeys.CONTRACT_COMPLETION_ALERT_DAYS);
        int frameworkDays = cfg.getInt(ConfigKeys.FRAMEWORK_EXPIRY_ALERT_DAYS);
        int annualDays = cfg.getInt(ConfigKeys.FRAMEWORK_ANNUAL_EVALUATION_ALERT_DAYS);
        for (Map<String, Object> c : jdbc.queryForList("""
                SELECT id, contract_ref, title, current_completion_date, is_framework, framework_expiry, signed_date
                  FROM contract WHERE status = 'ACTIVE'
                """)) {
            UUID id = (UUID) c.get("id");
            String link = "/n/contract/" + id;
            Set<UUID> to = new LinkedHashSet<>(cbms());
            to.addAll(jdbc.queryForList("""
                    SELECT DISTINCT procurement_officer_id FROM proc_file
                     WHERE contract_id = ? AND procurement_officer_id IS NOT NULL
                    """, UUID.class, id));
            LocalDate completion = ((Date) c.get("current_completion_date")).toLocalDate();
            if (!today.isAfter(completion) && !today.isBefore(completion.minusDays(completionDays))) {
                send(to, "CONTRACT_COMPLETION", List.of((String) c.get("contract_ref"), (String) c.get("title"), iso(completion)),
                        link, "contract", id, "contract:" + id + ":completion:" + completion);
            }
            if (Boolean.TRUE.equals(c.get("is_framework"))) {
                if (c.get("framework_expiry") != null) {
                    LocalDate expiry = ((Date) c.get("framework_expiry")).toLocalDate();
                    if (!today.isAfter(expiry) && !today.isBefore(expiry.minusDays(frameworkDays))) {
                        send(to, "FRAMEWORK_EXPIRY", List.of((String) c.get("contract_ref"), (String) c.get("title"), iso(expiry)),
                                link, "contract", id, "framework:" + id + ":expiry:" + expiry);
                    }
                }
                if (c.get("signed_date") != null) {
                    LocalDate signed = ((Date) c.get("signed_date")).toLocalDate();
                    long n = Math.max(1, ChronoUnit.YEARS.between(signed, today));
                    LocalDate anniversary = signed.plusYears(n);
                    if (anniversary.isBefore(today)) {
                        anniversary = signed.plusYears(n + 1);
                    }
                    if (!today.isAfter(anniversary) && !today.isBefore(anniversary.minusDays(annualDays))) {
                        send(to, "FRAMEWORK_ANNUAL_EVALUATION", List.of((String) c.get("contract_ref"), (String) c.get("title"),
                                iso(anniversary)), link, "contract", id, "framework:" + id + ":annual:" + anniversary);
                    }
                }
            }
        }
    }

    // ------------------------------------------------------------ committees

    void termExpiry(LocalDate today, ConfigSnapshot cfg) {
        int days = cfg.getInt(ConfigKeys.TERM_EXPIRY_ALERT_DAYS);
        for (Map<String, Object> m : jdbc.queryForList("""
                SELECT m.id, m.user_id, m.role, m.term_end, u.full_name, c.name AS committee_name
                  FROM committee_membership m
                  JOIN app_user u ON u.id = m.user_id
                  JOIN committee c ON c.id = m.committee_id
                 WHERE m.status = 'ACTIVE' AND m.term_end BETWEEN ? AND ?
                """, Date.valueOf(today), Date.valueOf(today.plusDays(days)))) {
            UUID id = (UUID) m.get("id");
            LocalDate end = ((Date) m.get("term_end")).toLocalDate();
            Set<UUID> to = new LinkedHashSet<>(cbms());
            to.add((UUID) m.get("user_id"));
            send(to, "TERM_EXPIRY", List.of((String) m.get("committee_name"), (String) m.get("full_name"),
                            humanise((String) m.get("role")), iso(end)), "/n/term/" + id, "committee_membership", id,
                    "term:" + id + ":" + end);
        }
    }

    void tasks(LocalDate today) {
        for (Map<String, Object> t : jdbc.queryForList("""
                SELECT id, kind, title, due_date, assignee_id FROM tracked_task
                 WHERE status = 'OPEN' AND due_date IS NOT NULL AND due_date <= ?
                """, Date.valueOf(today.plusDays(3)))) {
            UUID id = (UUID) t.get("id");
            LocalDate due = ((Date) t.get("due_date")).toLocalDate();
            Set<UUID> to = new LinkedHashSet<>();
            if (t.get("assignee_id") != null) {
                to.add((UUID) t.get("assignee_id"));
            }
            if (to.isEmpty() || ((String) t.get("kind")).startsWith("REGULATOR_NOTIFICATION")) {
                to.addAll(cbms());
            }
            boolean overdue = today.isAfter(due);
            send(to, overdue ? "TASK_OVERDUE" : "TASK_DUE", List.of((String) t.get("title"), iso(due)), "/n/task/" + id,
                    "tracked_task", id, "task:" + id + ":" + due + (overdue ? ":overdue" : ":due"));
        }
    }

    // -------------------------------------------------------------- sessions

    void upcomingSessions() {
        Instant now = Instant.now();
        for (Map<String, Object> s : jdbc.queryForList("""
                SELECT s.id, s.session_number, s.scheduled_at, s.committee_id, c.code
                  FROM committee_session s JOIN committee c ON c.id = s.committee_id
                 WHERE s.status = 'PLANNED' AND s.scheduled_at BETWEEN ? AND ?
                """, Timestamp.from(now), Timestamp.from(now.plus(48, ChronoUnit.HOURS)))) {
            UUID id = (UUID) s.get("id");
            Instant at = ((Timestamp) s.get("scheduled_at")).toInstant();
            var local = at.atZone(Time.KIGALI);
            List<UUID> members = jdbc.queryForList(
                    "SELECT user_id FROM committee_membership WHERE committee_id = ? AND status = 'ACTIVE'",
                    UUID.class, s.get("committee_id"));
            send(members, "SESSION_UPCOMING", List.of((String) s.get("session_number"), iso(local.toLocalDate()),
                            (String) s.get("code"), TIME.format(local)), "/n/session/" + id, "committee_session", id,
                    "session:" + id + ":upcoming:" + at.getEpochSecond());
        }
    }

    void pendingDeclarations() {
        for (Map<String, Object> p : jdbc.queryForList("""
                SELECT ai.id AS agenda_item_id, a.user_id, f.file_number, s.session_number
                  FROM committee_session s
                  JOIN agenda_item ai ON ai.session_id = s.id AND ai.file_id IS NOT NULL AND ai.status = 'PENDING'
                  JOIN proc_file f ON f.id = ai.file_id
                  JOIN attendance a ON a.session_id = s.id AND a.status = 'PRESENT'
                 WHERE s.status = 'IN_PROGRESS'
                   AND NOT EXISTS (SELECT 1 FROM coi_declaration d WHERE d.agenda_item_id = ai.id AND d.user_id = a.user_id)
                """)) {
            UUID item = (UUID) p.get("agenda_item_id");
            send(List.of((UUID) p.get("user_id")), "DECLARATION_PENDING",
                    List.of((String) p.get("file_number"), (String) p.get("session_number")),
                    "/n/declaration/" + item, "agenda_item", item, "coi:" + item);
        }
    }

    /** Daily reminder per minutes version while a present member has not acknowledged. */
    void minutesAwaitingAcknowledgement(LocalDate today) {
        for (Map<String, Object> m : jdbc.queryForList("""
                SELECT mi.id, mi.session_id, mi.version, a.user_id, s.session_number
                  FROM minutes mi
                  JOIN committee_session s ON s.id = mi.session_id
                  JOIN attendance a ON a.session_id = mi.session_id AND a.status = 'PRESENT'
                 WHERE mi.status = 'IN_REVIEW'
                   AND NOT EXISTS (SELECT 1 FROM minutes_acknowledgement k
                                    WHERE k.minutes_id = mi.id AND k.user_id = a.user_id AND k.minutes_version = mi.version)
                """)) {
            UUID id = (UUID) m.get("id");
            send(List.of((UUID) m.get("user_id")), "MINUTES_REVIEW", List.of((String) m.get("session_number")),
                    "/n/minutes/" + id + "?sessionId=" + m.get("session_id"), "minutes", id, "minutes:" + id + ":v" + m.get("version") + ":" + today);
        }
    }

    // --------------------------------------------------------------- helpers

    private void ensureTask(UUID committeeId, String kind, String title, String details, LocalDate due,
                            String entityType, UUID entityId, UUID assignee) {
        Integer existing = jdbc.queryForObject("""
                SELECT count(*) FROM tracked_task WHERE kind = ? AND entity_id = ? AND due_date = ?
                """, Integer.class, kind, entityId, Date.valueOf(due));
        if (existing != null && existing > 0) {
            return;
        }
        UUID id = Ids.newId();
        jdbc.update("""
                INSERT INTO tracked_task (id, institution_id, committee_id, kind, title, details, due_date,
                                          entity_type, entity_id, assignee_id)
                VALUES (?, app_current_institution(), ?, ?, ?, ?, ?, ?, ?, ?)
                """, id, committeeId, kind, title, details, Date.valueOf(due), entityType, entityId, assignee);
        audit.record("TASK_CREATED", "tracked_task", id, null,
                Map.of("kind", kind, "title", title, "dueDate", due.toString(), "source", "reminder-engine"));
    }

    private void send(Collection<UUID> users, String kind, List<String> args, String link, String entityType,
                      UUID entityId, String dedupeKey) {
        for (UUID u : users) {
            notifications.notify(u, kind, args, link, entityType, entityId, dedupeKey);
        }
    }

    /** Committee officers plus the assigned procurement officer. */
    private Set<UUID> fileTeam(UUID committeeId, UUID officer) {
        Set<UUID> to = new LinkedHashSet<>(jdbc.queryForList("""
                SELECT user_id FROM committee_membership
                 WHERE committee_id = ? AND status = 'ACTIVE' AND role IN ('CHAIRPERSON', 'VICE_CHAIRPERSON', 'SECRETARY')
                """, UUID.class, committeeId));
        if (officer != null) {
            to.add(officer);
        }
        return to;
    }

    private List<UUID> cbms() {
        return new ArrayList<>(jdbc.queryForList("""
                SELECT r.user_id FROM user_role r JOIN app_user u ON u.id = r.user_id
                 WHERE r.role = 'CBM' AND r.institution_id = app_current_institution() AND u.status = 'ACTIVE'
                """, UUID.class));
    }

    private static String iso(LocalDate d) {
        return d.toString();
    }

    private static String nz(String s) {
        return s == null ? "—" : s;
    }

    private static String humanise(String code) {
        String s = code.replace('_', ' ').toLowerCase();
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
