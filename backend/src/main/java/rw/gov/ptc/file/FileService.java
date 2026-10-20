package rw.gov.ptc.file;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import rw.gov.ptc.audit.AuditService;
import rw.gov.ptc.auth.api.InstitutionRole;
import rw.gov.ptc.calendar.WorkingDayCalendar;
import rw.gov.ptc.committee.Committee;
import rw.gov.ptc.committee.CommitteeRepository;
import rw.gov.ptc.common.ApiException;
import rw.gov.ptc.common.Rows;
import rw.gov.ptc.common.Time;
import rw.gov.ptc.config.ConfigKeys;
import rw.gov.ptc.config.ConfigService;
import rw.gov.ptc.contract.Contract;
import rw.gov.ptc.contract.ContractRepository;
import rw.gov.ptc.security.Access;
import rw.gov.ptc.session.AgendaService;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** The files register: what is assigned to which committee and how far it has got. */
@Service
public class FileService {

    private static final Set<String> TYPES = Set.of("A", "B", "C", "D", "E", "F", "G", "H", "I");
    private static final Set<String> PROCUREMENT_TYPES = Set.of("WORKS", "GOODS", "CONSULTANCY", "NON_CONSULTANCY");

    public record FileRequest(UUID committeeId, String itemType, String tenderRef, UUID contractId, String title,
                              String procurementType, String procurementMethod, UUID procurementOfficerId,
                              LocalDate dateReceived, LocalDate targetDate, String referralRef, String referralReason,
                              LocalDate bidValidityExpiry, Long version) {
    }

    public record Filter(UUID committeeId, String type, UUID officerId, String stage, Boolean overdue,
                         String procurementType, UUID contractId) {
    }

    private final ProcFileRepository files;
    private final FileStages stages;
    private final FileReassignmentRepository reassignments;
    private final OfficerAssignmentRepository officerAssignments;
    private final TableRequestRepository tableRequests;
    private final CommitteeRepository committees;
    private final ContractRepository contracts;
    private final Numbering numbering;
    private final ConfigService config;
    private final WorkingDayCalendar calendar;
    private final AuditService audit;
    private final Access access;
    private final JdbcTemplate jdbc;
    private final AgendaService agenda;

    public FileService(ProcFileRepository files, FileStages stages,
                       FileReassignmentRepository reassignments, OfficerAssignmentRepository officerAssignments,
                       TableRequestRepository tableRequests, CommitteeRepository committees,
                       ContractRepository contracts, Numbering numbering, ConfigService config,
                       WorkingDayCalendar calendar, AuditService audit, Access access, JdbcTemplate jdbc,
                       AgendaService agenda) {
        this.files = files;
        this.stages = stages;
        this.reassignments = reassignments;
        this.officerAssignments = officerAssignments;
        this.tableRequests = tableRequests;
        this.committees = committees;
        this.contracts = contracts;
        this.numbering = numbering;
        this.config = config;
        this.calendar = calendar;
        this.audit = audit;
        this.access = access;
        this.jdbc = jdbc;
        this.agenda = agenda;
    }

    /** Reads are limited to committees the user sits on, unless CBM / admin / read-only. */
    public ProcFile get(UUID id) {
        ProcFile f = files.findById(id).orElseThrow(() -> ApiException.notFound("File"));
        if (!access.canReadAllCommittees() && access.committeeRole(f.committeeId).isEmpty()) {
            throw ApiException.notFound("File");
        }
        return f;
    }

    private void requireEditor(UUID committeeId) {
        if (!access.has(InstitutionRole.CBM)) {
            access.requireOfficer(committeeId);
        }
    }

    // ---------------------------------------------------------------- list

    public List<Map<String, Object>> list(Filter f) {
        StringBuilder sql = new StringBuilder("""
                SELECT p.id, p.file_number, p.item_type, p.tender_ref, p.contract_id, k.contract_ref, p.title,
                       p.procurement_type, p.procurement_method, p.procurement_officer_id, u.full_name AS officer_name,
                       p.stage, p.date_received, p.target_date,
                       (p.closed_at IS NULL AND p.target_date < ?) AS overdue,
                       p.committee_id, c.code AS committee_code, p.bid_validity_expiry, p.created_at, p.version
                  FROM proc_file p
                  JOIN committee c ON c.id = p.committee_id
                  LEFT JOIN contract k ON k.id = p.contract_id
                  LEFT JOIN app_user u ON u.id = p.procurement_officer_id
                 WHERE 1 = 1
                """);
        List<Object> args = new ArrayList<>();
        args.add(java.sql.Date.valueOf(Time.today()));
        if (!access.canReadAllCommittees()) {
            List<UUID> mine = access.principal().committees().stream().map(g -> g.committeeId()).toList();
            if (mine.isEmpty()) {
                return List.of();
            }
            sql.append(" AND p.committee_id = ANY(?)");
            args.add(mine.toArray(new UUID[0]));
        }
        if (f.committeeId() != null) { sql.append(" AND p.committee_id = ?"); args.add(f.committeeId()); }
        if (f.type() != null) { sql.append(" AND p.item_type = ?"); args.add(f.type()); }
        if (f.officerId() != null) { sql.append(" AND p.procurement_officer_id = ?"); args.add(f.officerId()); }
        if (f.stage() != null) { sql.append(" AND p.stage = ?"); args.add(f.stage()); }
        if (f.procurementType() != null) { sql.append(" AND p.procurement_type = ?"); args.add(f.procurementType()); }
        if (f.contractId() != null) { sql.append(" AND p.contract_id = ?"); args.add(f.contractId()); }
        if (Boolean.TRUE.equals(f.overdue())) {
            sql.append(" AND p.closed_at IS NULL AND p.target_date < ?");
            args.add(java.sql.Date.valueOf(Time.today()));
        }
        sql.append(" ORDER BY p.date_received DESC, p.file_number DESC");
        return Rows.camel(jdbc.queryForList(sql.toString(), args.toArray()));
    }

    // -------------------------------------------------------------- create

    @Transactional
    public ProcFile create(FileRequest req) {
        if (req.committeeId() == null || req.itemType() == null || !TYPES.contains(req.itemType())) {
            throw ApiException.badRequest("file.invalid", "Committee and a valid item type are required");
        }
        requireEditor(req.committeeId());
        Committee c = committees.findById(req.committeeId()).orElseThrow(() -> ApiException.notFound("Committee"));
        if (!"ACTIVE".equals(c.status)) {
            throw ApiException.rule("committee.inactive", "The committee is dissolved");
        }
        if (req.title() == null || req.title().isBlank()) {
            throw ApiException.badRequest("file.title_required", "Title is required");
        }
        ProcFile f = new ProcFile();
        f.committeeId = c.id;
        f.itemType = req.itemType();
        apply(f, req);
        f.dateReceived = req.dateReceived() == null ? Time.today() : req.dateReceived();
        if (f.targetDate == null) {
            var targets = config.snapshot().get(ConfigKeys.WORKFLOW_TARGET_WORKING_DAYS);
            if (targets.has(f.itemType)) {
                f.targetDate = calendar.addWorkingDays(f.dateReceived, targets.get(f.itemType).asInt());
            }
        }
        validate(f);
        int year = f.dateReceived.getYear();
        f.fileNumber = "F-%d/%04d".formatted(year, numbering.next("FILE:" + year));
        f.openedAt = Instant.now();
        f.createdBy = access.userId();
        files.saveAndFlush(f);
        audit.created("proc_file", f.id, f);
        if (f.procurementOfficerId != null) {
            recordOfficer(f, f.procurementOfficerId, financialYear(f.dateReceived));
        }
        return f;
    }

    private void apply(ProcFile f, FileRequest req) {
        if (req.title() != null && !req.title().isBlank()) f.title = req.title().trim();
        if (req.tenderRef() != null) f.tenderRef = blankToNull(req.tenderRef());
        if (req.contractId() != null) f.contractId = req.contractId();
        if (req.procurementType() != null) f.procurementType = blankToNull(req.procurementType());
        if (req.procurementMethod() != null) f.procurementMethod = blankToNull(req.procurementMethod());
        if (req.procurementOfficerId() != null) f.procurementOfficerId = req.procurementOfficerId();
        if (req.targetDate() != null) f.targetDate = req.targetDate();
        if (req.referralRef() != null) f.referralRef = blankToNull(req.referralRef());
        if (req.referralReason() != null) f.referralReason = blankToNull(req.referralReason());
        if (req.bidValidityExpiry() != null) f.bidValidityExpiry = req.bidValidityExpiry();
    }

    private void validate(ProcFile f) {
        if (f.procurementType != null && !PROCUREMENT_TYPES.contains(f.procurementType)) {
            throw ApiException.badRequest("file.invalid_procurement_type", "Unknown procurement type");
        }
        if ("H".equals(f.itemType) && (f.referralRef == null || f.referralReason == null)) {
            throw ApiException.rule("file.referral_required",
                    "A referred matter needs the reference of the CBM's referral and the stated reason");
        }
        if (Set.of("C", "D", "G").contains(f.itemType)) {
            if (f.contractId == null) {
                throw ApiException.rule("file.contract_required", "This item type concerns a signed contract: choose it");
            }
        }
        if (f.contractId != null) {
            Contract k = contracts.findById(f.contractId).orElseThrow(() -> ApiException.notFound("Contract"));
            if ("G".equals(f.itemType) && !k.isFramework) {
                throw ApiException.rule("file.framework_required", "A framework evaluation needs a framework agreement");
            }
            if (f.procurementType == null) {
                f.procurementType = k.procurementType;
            }
            if (f.tenderRef == null) {
                f.tenderRef = k.tenderRef;
            }
        }
        if (f.procurementOfficerId != null) {
            requireProcurementOfficer(f.procurementOfficerId);
        }
        if (f.targetDate != null && f.targetDate.isBefore(f.dateReceived)) {
            throw ApiException.badRequest("file.invalid_target", "Target date cannot be before the date received");
        }
    }

    private void requireProcurementOfficer(UUID userId) {
        Boolean po = jdbc.query("SELECT is_procurement_officer FROM app_user WHERE id = ?",
                rs -> rs.next() ? rs.getBoolean(1) : null, userId);
        if (po == null) {
            throw ApiException.notFound("User");
        }
        if (!po) {
            throw ApiException.rule("file.not_procurement_officer", "The assigned officer must be a procurement officer");
        }
    }

    // -------------------------------------------------------------- detail

    public Map<String, Object> detail(UUID id) {
        ProcFile f = get(id);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("file", f);
        out.put("committee", Rows.camel(jdbc.queryForMap("SELECT id, name, code FROM committee WHERE id = ?", f.committeeId)));
        out.put("contract", f.contractId == null ? null : contracts.findById(f.contractId).orElse(null));
        out.put("officerName", f.procurementOfficerId == null ? null
                : jdbc.queryForObject("SELECT full_name FROM app_user WHERE id = ?", String.class, f.procurementOfficerId));
        out.put("overdue", f.closedAt == null && f.targetDate != null && f.targetDate.isBefore(Time.today()));
        out.put("allowedStages", stages.stagesFor(f.itemType));
        out.put("stageHistory", Rows.camel(jdbc.queryForList("""
                SELECT h.from_stage, h.to_stage, h.note, h.changed_at, u.full_name AS changed_by_name
                  FROM file_stage_history h LEFT JOIN app_user u ON u.id = h.changed_by
                 WHERE h.file_id = ? ORDER BY h.changed_at
                """, id)));
        out.put("reassignments", Rows.camel(jdbc.queryForList("""
                SELECT r.reason, r.performed_at, a.code AS from_committee, b.code AS to_committee, u.full_name AS performed_by_name
                  FROM file_reassignment r JOIN committee a ON a.id = r.from_committee_id
                  JOIN committee b ON b.id = r.to_committee_id LEFT JOIN app_user u ON u.id = r.performed_by
                 WHERE r.file_id = ? ORDER BY r.performed_at
                """, id)));
        out.put("officerAssignments", Rows.camel(jdbc.queryForList("""
                SELECT o.financial_year, o.assigned_at, u.full_name AS officer_name
                  FROM officer_assignment o JOIN app_user u ON u.id = o.officer_id
                 WHERE o.file_id = ? ORDER BY o.assigned_at
                """, id)));
        out.put("decisions", Rows.camel(jdbc.queryForList("""
                SELECT d.id, d.status, d.recommendation_code, d.recommendation_text, d.votes_for, d.votes_against,
                       d.votes_abstained, d.required_votes, d.issued_at, s.session_number, s.id AS session_id,
                       d.agenda_item_id
                  FROM decision d JOIN committee_session s ON s.id = d.session_id
                 WHERE d.file_id = ? ORDER BY d.created_at
                """, id)));
        out.put("cbmResponses", Rows.camel(jdbc.queryForList("""
                SELECT r.id, r.decision_id, r.response, r.reasons, r.responded_at, u.full_name AS responded_by_name
                  FROM cbm_response r LEFT JOIN app_user u ON u.id = r.responded_by
                 WHERE r.file_id = ? ORDER BY r.responded_at
                """, id)));
        out.put("agenda", Rows.camel(jdbc.queryForList("""
                SELECT a.id AS agenda_item_id, s.id AS session_id, s.session_number, s.scheduled_at, s.status
                  FROM agenda_item a JOIN committee_session s ON s.id = a.session_id
                 WHERE a.file_id = ? ORDER BY s.scheduled_at
                """, id)));
        out.put("tableRequests", tableRequests.findByFileIdOrderByRequestedAtDesc(id));
        return out;
    }

    // -------------------------------------------------------------- update

    @Transactional
    public ProcFile update(UUID id, FileRequest req) {
        ProcFile f = get(id);
        requireEditor(f.committeeId);
        if (req.version() != null && !req.version().equals(f.version)) {
            throw ApiException.conflict("concurrent_modification", "Reload and try again");
        }
        if (f.closedAt != null) {
            throw ApiException.rule("file.closed", "This file is closed");
        }
        var before = audit.snapshot(f);
        UUID officerBefore = f.procurementOfficerId;
        apply(f, req);
        validate(f);
        files.saveAndFlush(f);
        audit.updated("proc_file", f.id, before, f);
        if (f.procurementOfficerId != null && !f.procurementOfficerId.equals(officerBefore)) {
            recordOfficer(f, f.procurementOfficerId, financialYear(Time.today()));
        }
        return f;
    }

    @Transactional
    public ProcFile changeStage(UUID id, String toStage, String note) {
        ProcFile f = get(id);
        requireEditor(f.committeeId);
        if (toStage == null) {
            throw ApiException.badRequest("file.invalid_stage", "Choose a stage");
        }
        stages.move(f, toStage, note);
        return f;
    }

    @Transactional
    public ProcFile reassign(UUID id, UUID toCommitteeId, String reason) {
        access.requireCbm();
        ProcFile f = get(id);
        if (reason == null || reason.isBlank()) {
            throw ApiException.badRequest("file.reason_required", "Give the reason for reassigning this file");
        }
        if (toCommitteeId == null) {
            throw ApiException.badRequest("file.committee_required", "Choose the receiving committee");
        }
        Committee to = committees.findById(toCommitteeId).orElseThrow(() -> ApiException.notFound("Committee"));
        if (to.id.equals(f.committeeId)) {
            throw ApiException.badRequest("file.same_committee", "The file is already with this committee");
        }
        if (!"ACTIVE".equals(to.status)) {
            throw ApiException.rule("committee.inactive", "The committee is dissolved");
        }
        var before = audit.snapshot(f);
        FileReassignment r = new FileReassignment();
        r.fileId = f.id;
        r.fromCommitteeId = f.committeeId;
        r.toCommitteeId = to.id;
        r.reason = reason.trim();
        r.performedBy = access.userId();
        reassignments.save(r);
        f.committeeId = to.id;
        files.saveAndFlush(f);
        audit.record("REASSIGN", "proc_file", f.id, before, f);
        return f;
    }

    @Transactional
    public ProcFile assignOfficer(UUID id, UUID officerId, String financialYear) {
        access.requireCbm();
        ProcFile f = get(id);
        if (officerId == null) {
            throw ApiException.badRequest("file.officer_required", "Choose an officer");
        }
        requireProcurementOfficer(officerId);
        var before = audit.snapshot(f);
        f.procurementOfficerId = officerId;
        files.saveAndFlush(f);
        audit.updated("proc_file", f.id, before, f);
        recordOfficer(f, officerId, financialYear == null || financialYear.isBlank()
                ? financialYear(Time.today()) : financialYear);
        return f;
    }

    private void recordOfficer(ProcFile f, UUID officerId, String fy) {
        OfficerAssignment o = new OfficerAssignment();
        o.fileId = f.id;
        o.officerId = officerId;
        o.financialYear = fy;
        o.assignedBy = access.userId();
        officerAssignments.save(o);
        audit.record("ASSIGN_OFFICER", "officer_assignment", o.id, null, o);
    }

    /** Rwanda's financial year runs 1 July – 30 June. */
    static String financialYear(LocalDate d) {
        int start = d.getMonthValue() >= 7 ? d.getYear() : d.getYear() - 1;
        return start + "/" + (start + 1);
    }

    // ------------------------------------------------------ table requests

    @Transactional
    public TableRequest requestTabling(UUID fileId, String reason) {
        ProcFile f = get(fileId);
        access.requireMember(f.committeeId);
        if (f.closedAt != null) {
            throw ApiException.rule("file.closed", "This file is closed");
        }
        TableRequest t = new TableRequest();
        t.committeeId = f.committeeId;
        t.fileId = f.id;
        t.requestedBy = access.userId();
        t.reason = reason;
        tableRequests.saveAndFlush(t);
        audit.created("table_request", t.id, t);
        return t;
    }

    public List<Map<String, Object>> tableRequests(UUID committeeId, String status) {
        access.requireCanRead(committeeId);
        return Rows.camel(jdbc.queryForList("""
                SELECT t.id, t.file_id, p.file_number, p.title, p.item_type, t.reason, t.status, t.requested_at,
                       r.full_name AS requested_by_name, t.decided_at, t.decision_note, t.version
                  FROM table_request t JOIN proc_file p ON p.id = t.file_id
                  LEFT JOIN app_user r ON r.id = t.requested_by
                 WHERE t.committee_id = ? AND (CAST(? AS text) IS NULL OR t.status = ?)
                 ORDER BY t.requested_at DESC
                """, committeeId, status, status));
    }

    @Transactional
    public TableRequest decideTabling(UUID requestId, boolean accept, String note, UUID sessionId) {
        TableRequest t = tableRequests.findById(requestId).orElseThrow(() -> ApiException.notFound("Request"));
        access.requireChair(t.committeeId);
        if (!"PENDING".equals(t.status)) {
            throw ApiException.conflict("table_request.decided", "This request has already been decided");
        }
        var before = audit.snapshot(t);
        t.status = accept ? "ACCEPTED" : "DECLINED";
        t.decidedBy = access.userId();
        t.decidedAt = Instant.now();
        t.decisionNote = note;
        tableRequests.saveAndFlush(t);
        audit.updated("table_request", t.id, before, t);
        if (accept && sessionId != null) {
            ProcFile f = files.findById(t.fileId).orElseThrow();
            agenda.add(sessionId, new AgendaService.AddItem(f.id, f.itemType, null, null));
        }
        return t;
    }


    static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
