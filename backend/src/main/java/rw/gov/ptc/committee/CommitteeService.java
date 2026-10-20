package rw.gov.ptc.committee;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import rw.gov.ptc.audit.AuditService;
import rw.gov.ptc.auth.api.IdentityAdmin;
import rw.gov.ptc.common.ApiException;
import rw.gov.ptc.common.Time;
import rw.gov.ptc.config.ConfigKeys;
import rw.gov.ptc.config.ConfigService;
import rw.gov.ptc.config.ConfigSnapshot;
import rw.gov.ptc.security.Access;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Committee constitution by the Chief Budget Manager. */
@Service
public class CommitteeService {

    private static final Set<String> ROLES = Set.of("CHAIRPERSON", "VICE_CHAIRPERSON", "SECRETARY", "MEMBER");

    public record CreateCommittee(String name, String code, Integer size, String regulatorAuthRef,
                                  LocalDate regulatorAuthDate) {
    }

    public record UpdateCommittee(String name, Integer size, String regulatorAuthRef, LocalDate regulatorAuthDate,
                                  Long version) {
    }

    public record Appointment(UUID userId, String role, LocalDate termStart, LocalDate termEnd,
                              String appointmentRef, UUID renewMembershipId) {
    }

    public record Replacement(String scope, String reason, List<UUID> outgoingMembershipIds,
                              List<Appointment> appointments) {
    }

    private final CommitteeRepository committees;
    private final MembershipRepository memberships;
    private final MembershipReplacementRepository replacements;
    private final TrackedTaskRepository tasks;
    private final CompositionRules rules;
    private final ConfigService config;
    private final AuditService audit;
    private final IdentityAdmin identity;
    private final Access access;
    private final JdbcTemplate jdbc;

    public CommitteeService(CommitteeRepository committees, MembershipRepository memberships,
                            MembershipReplacementRepository replacements, TrackedTaskRepository tasks,
                            CompositionRules rules, ConfigService config, AuditService audit, IdentityAdmin identity,
                            Access access, JdbcTemplate jdbc) {
        this.committees = committees;
        this.memberships = memberships;
        this.replacements = replacements;
        this.tasks = tasks;
        this.rules = rules;
        this.config = config;
        this.audit = audit;
        this.identity = identity;
        this.access = access;
        this.jdbc = jdbc;
    }

    public Committee get(UUID id) {
        return committees.findById(id).orElseThrow(() -> ApiException.notFound("Committee"));
    }

    // ------------------------------------------------------------ committees

    @Transactional
    public Committee create(CreateCommittee req) {
        access.requireCbm();
        if (blank(req.name()) || blank(req.code()) || req.size() == null) {
            throw ApiException.badRequest("committee.invalid", "Name, code and size are required");
        }
        ConfigSnapshot cfg = config.snapshot();
        checkSize(req.size(), cfg);
        if (committees.countByStatus("ACTIVE") >= 1 && (blank(req.regulatorAuthRef()) || req.regulatorAuthDate() == null)) {
            throw ApiException.rule("committee.regulator_authorisation_required",
                    "An additional committee requires the regulator's authorisation: record its reference and date");
        }
        Committee c = new Committee();
        c.name = req.name().trim();
        c.code = req.code().trim().toUpperCase();
        c.size = req.size();
        c.regulatorAuthRef = req.regulatorAuthRef();
        c.regulatorAuthDate = req.regulatorAuthDate();
        c.createdBy = access.userId();
        committees.saveAndFlush(c);
        audit.created("committee", c.id, c);
        return c;
    }

    @Transactional
    public Committee update(UUID id, UpdateCommittee req) {
        access.requireCbm();
        Committee c = get(id);
        if (req.version() != null && !req.version().equals(c.version)) {
            throw ApiException.conflict("concurrent_modification", "Reload and try again");
        }
        var before = audit.snapshot(c);
        if (!blank(req.name())) c.name = req.name().trim();
        if (req.size() != null && req.size() != c.size) {
            checkSize(req.size(), config.snapshot());
            int active = memberships.findByCommitteeIdAndStatusOrderByRoleAsc(id, "ACTIVE").size();
            if (req.size() < active) {
                throw ApiException.rule("committee.size_below_members",
                        "End memberships before reducing the committee below its current " + active + " members",
                        Map.of("active", active));
            }
            c.size = req.size();
        }
        if (req.regulatorAuthRef() != null) c.regulatorAuthRef = req.regulatorAuthRef();
        if (req.regulatorAuthDate() != null) c.regulatorAuthDate = req.regulatorAuthDate();
        committees.saveAndFlush(c);
        audit.updated("committee", c.id, before, c);
        return c;
    }

    private void checkSize(int size, ConfigSnapshot cfg) {
        List<Integer> allowed = new ArrayList<>();
        cfg.get(ConfigKeys.COMMITTEE_ALLOWED_SIZES).forEach(n -> allowed.add(n.asInt()));
        int minimum = cfg.getInt(ConfigKeys.COMMITTEE_MINIMUM_SIZE);
        int smallestStandard = allowed.stream().min(Integer::compare).orElse(minimum);
        boolean ok = allowed.contains(size) || (size >= minimum && size < smallestStandard);
        if (!ok) {
            throw ApiException.rule("committee.size_not_allowed",
                    "Committee size must be one of " + allowed + " (or at least " + minimum + " for small institutions)",
                    Map.of("allowed", allowed, "minimum", minimum));
        }
    }

    // ----------------------------------------------------------- composition

    public List<Membership> active(UUID committeeId) {
        return memberships.findByCommitteeIdAndStatusOrderByRoleAsc(committeeId, "ACTIVE");
    }

    public CompositionRules.Result composition(UUID committeeId) {
        Committee c = get(committeeId);
        List<CompositionRules.Seat> seats = active(committeeId).stream()
                .map(m -> new CompositionRules.Seat(m.userId, m.role, m.consecutiveTerm, false)).toList();
        return rules.evaluate(c, seats, config.snapshot());
    }

    /** Composition after removing some seats and appointing others. */
    public CompositionRules.Result simulate(UUID committeeId, Set<UUID> outgoing, List<Appointment> appointments) {
        Committee c = get(committeeId);
        List<CompositionRules.Seat> seats = new ArrayList<>();
        for (Membership m : active(committeeId)) {
            if (!outgoing.contains(m.id)) {
                seats.add(new CompositionRules.Seat(m.userId, m.role, m.consecutiveTerm, false));
            }
        }
        for (Appointment a : appointments) {
            validateAppointment(a);
            LocalDate start = a.termStart() == null ? Time.today() : a.termStart();
            List<Membership> previous = memberships.findByCommitteeIdAndUserIdOrderByTermEndDesc(committeeId, a.userId());
            seats.add(new CompositionRules.Seat(a.userId(), a.role(), CompositionRules.consecutiveTerm(previous, start), true));
        }
        return rules.evaluate(c, seats, config.snapshot());
    }

    private static void validateAppointment(Appointment a) {
        if (a.userId() == null || a.role() == null || !ROLES.contains(a.role())) {
            throw ApiException.badRequest("committee.invalid_appointment", "Choose a person and a valid role");
        }
    }

    @Transactional
    public Map<String, Object> appoint(UUID committeeId, Appointment a) {
        access.requireCbm();
        Committee c = get(committeeId);
        if (!"ACTIVE".equals(c.status)) {
            throw ApiException.rule("committee.inactive", "The committee is dissolved");
        }
        Set<UUID> outgoing = a.renewMembershipId() == null ? Set.of() : Set.of(a.renewMembershipId());
        CompositionRules.Result result = simulate(committeeId, outgoing, List.of(a));
        result.throwIfBlocked();
        if (a.renewMembershipId() != null) {
            Membership old = membershipOf(committeeId, a.renewMembershipId());
            if (!old.userId.equals(a.userId())) {
                throw ApiException.badRequest("committee.invalid_renewal", "A renewal must be for the same person");
            }
            endMembership(old, "ENDED", "Renewed for a further term", null);
        }
        Membership m = newMembership(c, a);
        audit.record("APPOINT", "committee_membership", m.id, null, m);
        AfterCommit.run(() -> identity.membershipChanged(a.userId()));
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("membership", m);
        out.put("warnings", result.warnings());
        return out;
    }

    private Membership newMembership(Committee c, Appointment a) {
        ConfigSnapshot cfg = config.snapshot();
        LocalDate start = a.termStart() == null ? Time.today() : a.termStart();
        LocalDate end = a.termEnd() != null ? a.termEnd()
                : start.plusMonths(cfg.getInt(ConfigKeys.TERM_LENGTH_MONTHS)).minusDays(1);
        if (!end.isAfter(start)) {
            throw ApiException.badRequest("committee.invalid_term", "Term end must be after term start");
        }
        Membership m = new Membership();
        m.committeeId = c.id;
        m.userId = a.userId();
        m.role = a.role();
        m.termStart = start;
        m.termEnd = end;
        m.consecutiveTerm = CompositionRules.consecutiveTerm(
                memberships.findByCommitteeIdAndUserIdOrderByTermEndDesc(c.id, a.userId()), start);
        m.appointmentRef = a.appointmentRef();
        m.appointedBy = access.userId();
        memberships.saveAndFlush(m);
        return m;
    }

    @Transactional
    public Membership end(UUID committeeId, UUID membershipId, String reason) {
        access.requireCbm();
        if (blank(reason)) {
            throw ApiException.badRequest("committee.reason_required", "Give the reason for ending this membership");
        }
        Membership m = membershipOf(committeeId, membershipId);
        endMembership(m, "ENDED", reason, null);
        return m;
    }

    private void endMembership(Membership m, String status, String reason, UUID replacementId) {
        if (!"ACTIVE".equals(m.status)) {
            throw ApiException.conflict("committee.membership_not_active", "This membership has already ended");
        }
        var before = audit.snapshot(m);
        m.status = status;
        m.endedAt = Instant.now();
        m.endReason = reason;
        m.replacementId = replacementId;
        memberships.saveAndFlush(m);
        audit.record("REPLACED".equals(status) ? "REPLACE" : "END_MEMBERSHIP", "committee_membership", m.id, before, m);
        UUID user = m.userId;
        AfterCommit.run(() -> identity.membershipChanged(user));
    }

    private Membership membershipOf(UUID committeeId, UUID membershipId) {
        Membership m = memberships.findById(membershipId).orElseThrow(() -> ApiException.notFound("Membership"));
        if (!m.committeeId.equals(committeeId)) {
            throw ApiException.notFound("Membership");
        }
        return m;
    }

    /**
     * Replace one or all members. Replacement requires sound, recorded
     * reasons; replacing ALL members also opens a tracked task to notify the
     * regulator within the configured period.
     */
    @Transactional
    public Map<String, Object> replace(UUID committeeId, Replacement req) {
        access.requireCbm();
        Committee c = get(committeeId);
        if (blank(req.reason()) || req.reason().trim().length() < 10) {
            throw ApiException.badRequest("committee.replacement_reason_required",
                    "Replacement requires sound reasons: describe them (at least 10 characters)");
        }
        List<Membership> active = active(committeeId);
        Set<UUID> outgoing;
        if ("ALL".equals(req.scope())) {
            outgoing = new HashSet<>(active.stream().map(m -> m.id).toList());
        } else if ("SINGLE".equals(req.scope())) {
            if (req.outgoingMembershipIds() == null || req.outgoingMembershipIds().size() != 1) {
                throw ApiException.badRequest("committee.replacement_single", "Choose the one member being replaced");
            }
            outgoing = Set.of(req.outgoingMembershipIds().get(0));
            membershipOf(committeeId, req.outgoingMembershipIds().get(0));
        } else {
            throw ApiException.badRequest("committee.replacement_scope", "Scope must be SINGLE or ALL");
        }
        List<Appointment> appointments = req.appointments() == null ? List.of() : req.appointments();
        CompositionRules.Result result = simulate(committeeId, outgoing, appointments);
        result.throwIfBlocked();

        ConfigSnapshot cfg = config.snapshot();
        TrackedTask task = null;
        if ("ALL".equals(req.scope())) {
            task = new TrackedTask();
            task.committeeId = committeeId;
            task.kind = "REGULATOR_NOTIFICATION";
            task.title = "Notify the regulator of the replacement of all members of " + c.name;
            task.details = req.reason();
            task.dueDate = Time.today().plusDays(cfg.getInt(ConfigKeys.REGULATOR_NOTIFICATION_DAYS));
            task.entityType = "committee";
            task.entityId = committeeId;
            task.assigneeId = access.userId();
            task.createdBy = access.userId();
            tasks.saveAndFlush(task);
            audit.created("tracked_task", task.id, task);
        }
        MembershipReplacement r = new MembershipReplacement();
        r.committeeId = committeeId;
        r.scope = req.scope();
        r.reason = req.reason().trim();
        r.performedBy = access.userId();
        r.regulatorTaskId = task == null ? null : task.id;
        replacements.saveAndFlush(r);
        for (Membership m : active) {
            if (outgoing.contains(m.id)) {
                endMembership(m, "REPLACED", r.reason, r.id);
            }
        }
        List<Membership> appointed = new ArrayList<>();
        for (Appointment a : appointments) {
            Membership m = newMembership(c, a);
            audit.record("APPOINT", "committee_membership", m.id, null, m);
            AfterCommit.run(() -> identity.membershipChanged(a.userId()));
            appointed.add(m);
        }
        audit.record("REPLACE_MEMBERS", "membership_replacement", r.id, null, r);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("replacement", r);
        out.put("appointed", appointed);
        out.put("regulatorTask", task);
        out.put("warnings", result.warnings());
        return out;
    }

    public List<MembershipReplacement> replacements(UUID committeeId) {
        return replacements.findByCommitteeIdOrderByPerformedAtDesc(committeeId);
    }

    // ------------------------------------------------------------------ views

    public List<Map<String, Object>> members(UUID committeeId) {
        return rw.gov.ptc.common.Rows.camel(jdbc.queryForList("""
                SELECT m.id AS membership_id, m.user_id, u.full_name, u.email, m.role, m.term_start, m.term_end,
                       m.consecutive_term, m.status, u.is_procurement_officer, m.appointment_ref, m.version
                  FROM committee_membership m JOIN app_user u ON u.id = m.user_id
                 WHERE m.committee_id = ? AND m.status = 'ACTIVE'
                 ORDER BY CASE m.role WHEN 'CHAIRPERSON' THEN 1 WHEN 'VICE_CHAIRPERSON' THEN 2
                                      WHEN 'SECRETARY' THEN 3 ELSE 4 END, u.full_name
                """, committeeId));
    }

    public List<Map<String, Object>> candidates(UUID committeeId) {
        Committee c = get(committeeId);
        Set<String> excluded = new HashSet<>(config.snapshot().getStrings(ConfigKeys.EXCLUDED_OFFICES));
        Set<UUID> current = new HashSet<>(active(committeeId).stream().map(m -> m.userId).toList());
        List<Map<String, Object>> out = new ArrayList<>();
        jdbc.query("""
                SELECT u.id, u.full_name, u.email, u.job_title, u.office_holder_code, u.is_procurement_officer, u.status,
                       EXISTS (SELECT 1 FROM user_role r WHERE r.user_id = u.id AND r.role = 'CBM') AS cbm
                  FROM app_user u WHERE u.status IN ('ACTIVE', 'INVITED') AND u.institution_id IS NOT NULL
                 ORDER BY u.full_name
                """, rs -> {
            UUID id = rs.getObject("id", UUID.class);
            String office = rs.getString("office_holder_code");
            String reason = null;
            if (rs.getBoolean("cbm") || "CBM".equals(office)) {
                reason = "composition.cbm_cannot_be_member";
            } else if (office != null && excluded.contains(office)) {
                reason = "composition.excluded_office";
            } else if (current.contains(id)) {
                reason = "composition.already_member";
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("userId", id);
            row.put("fullName", rs.getString("full_name"));
            row.put("email", rs.getString("email"));
            row.put("jobTitle", rs.getString("job_title"));
            row.put("officeHolderCode", office);
            row.put("isProcurementOfficer", rs.getBoolean("is_procurement_officer"));
            row.put("status", rs.getString("status"));
            row.put("eligible", reason == null);
            row.put("ineligibleReason", reason);
            out.add(row);
        });
        return out;
    }

    // ------------------------------------------------------------------ tasks

    @Transactional
    public TrackedTask completeTask(UUID taskId, String reference, String note) {
        TrackedTask t = tasks.findById(taskId).orElseThrow(() -> ApiException.notFound("Task"));
        boolean allowed = access.has(rw.gov.ptc.auth.api.InstitutionRole.CBM)
                || access.has(rw.gov.ptc.auth.api.InstitutionRole.INSTITUTION_ADMIN)
                || (t.committeeId != null && access.committeeRole(t.committeeId).map(r -> r.isOfficer()).orElse(false));
        if (!allowed) {
            throw ApiException.forbidden("forbidden", "You cannot complete this task");
        }
        if (!"OPEN".equals(t.status)) {
            throw ApiException.conflict("task.not_open", "This task is already closed");
        }
        if ("REGULATOR_NOTIFICATION".equals(t.kind) && blank(reference)) {
            throw ApiException.badRequest("task.reference_required",
                    "Record the reference of the letter or message sent to the regulator");
        }
        var before = audit.snapshot(t);
        t.status = "DONE";
        t.doneAt = Instant.now();
        t.doneBy = access.userId();
        t.doneReference = reference;
        t.doneNote = note;
        tasks.saveAndFlush(t);
        audit.updated("tracked_task", t.id, before, t);
        return t;
    }

    static boolean blank(String s) {
        return s == null || s.isBlank();
    }
}
