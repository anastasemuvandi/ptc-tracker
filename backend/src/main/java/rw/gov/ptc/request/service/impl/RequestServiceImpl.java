package rw.gov.ptc.request.service.impl;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import rw.gov.ptc.audit.service.AuditService;
import rw.gov.ptc.auth.api.InstitutionRole;
import rw.gov.ptc.common.ApiException;
import rw.gov.ptc.common.Enums;
import rw.gov.ptc.common.Rows;
import rw.gov.ptc.common.Time;
import rw.gov.ptc.config.component.ConfigKeys;
import rw.gov.ptc.config.dto.ConfigSnapshot;
import rw.gov.ptc.config.service.ConfigService;
import rw.gov.ptc.evaluation.component.FileAccess;
import rw.gov.ptc.file.component.Numbering;
import rw.gov.ptc.file.dto.FileRequest;
import rw.gov.ptc.file.entity.ProcFile;
import rw.gov.ptc.file.service.FileService;
import rw.gov.ptc.institution.entity.AppUser;
import rw.gov.ptc.institution.repository.AppUserRepository;
import rw.gov.ptc.notification.service.NotificationService;
import rw.gov.ptc.request.component.DocumentSignatures;
import rw.gov.ptc.request.dto.AcceptRequest;
import rw.gov.ptc.request.dto.RequestInput;
import rw.gov.ptc.request.dto.ScheduleRequest;
import rw.gov.ptc.request.entity.PtcRequest;
import rw.gov.ptc.request.entity.RequestDocument;
import rw.gov.ptc.request.enums.RequestStatus;
import rw.gov.ptc.request.repository.PtcRequestRepository;
import rw.gov.ptc.request.repository.RequestDocumentRepository;
import rw.gov.ptc.request.service.RequestService;
import rw.gov.ptc.security.Access;
import rw.gov.ptc.session.dto.AddItem;
import rw.gov.ptc.session.service.AgendaService;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class RequestServiceImpl implements RequestService {

    private static final Set<String> NEEDS_CONTRACT = Set.of("C", "D");
    private static final Set<RequestStatus> EDITABLE = Set.of(RequestStatus.DRAFT, RequestStatus.RETURNED);

    private final PtcRequestRepository requests;
    private final RequestDocumentRepository documents;
    private final AppUserRepository users;
    private final FileService files;
    private final FileAccess fileAccess;
    private final Numbering numbering;
    private final ConfigService config;
    private final NotificationService notifications;
    private final AuditService audit;
    private final Access access;
    private final JdbcTemplate jdbc;
    private final AgendaService agenda;

    public RequestServiceImpl(PtcRequestRepository requests, RequestDocumentRepository documents, AppUserRepository users,
                              FileService files, FileAccess fileAccess, Numbering numbering, ConfigService config,
                              NotificationService notifications, AuditService audit, Access access, JdbcTemplate jdbc,
                              AgendaService agenda) {
        this.agenda = agenda;
        this.requests = requests;
        this.documents = documents;
        this.users = users;
        this.files = files;
        this.fileAccess = fileAccess;
        this.numbering = numbering;
        this.config = config;
        this.notifications = notifications;
        this.audit = audit;
        this.access = access;
        this.jdbc = jdbc;
    }

    // ---------------------------------------------------------------- access

    /** A requester whose access has not ended. */
    private void requireRequester() {
        access.requireAny(InstitutionRole.REQUESTER);
        AppUser me = users.findById(access.userId()).orElseThrow(() -> ApiException.notFound("User"));
        if (me.accessUntil != null && me.accessUntil.isBefore(Time.today())) {
            throw ApiException.forbidden("request.access_ended", "Your access as a requester has ended");
        }
    }

    private boolean canReview() {
        return access.isOfficerAnywhere() || access.canReadAllCommittees();
    }

    private void requireOfficerAnywhere() {
        if (!access.isOfficerAnywhere()) {
            throw ApiException.forbidden("committee.officer_required",
                    "Only the Chairperson, Vice-Chairperson or Secretary may do this");
        }
    }

    private PtcRequest own(UUID id) {
        requireRequester();
        return requests.findById(id).filter(r -> r.requesterId.equals(access.userId()))
                .orElseThrow(() -> ApiException.notFound("Request"));
    }

    private PtcRequest editable(UUID id) {
        PtcRequest r = own(id);
        if (!EDITABLE.contains(r.status)) {
            throw ApiException.rule("request.not_editable", "Only a draft or returned request can be changed");
        }
        return r;
    }

    /** The requester, or a reviewer once the request is submitted. */
    private PtcRequest readable(UUID id) {
        PtcRequest r = requests.findById(id).orElseThrow(() -> ApiException.notFound("Request"));
        boolean mine = r.requesterId.equals(access.userId());
        if (!mine && !(canReview() && r.status != RequestStatus.DRAFT)) {
            throw ApiException.notFound("Request");
        }
        return r;
    }

    // ----------------------------------------------------------------- views

    private static final String LIST_SQL = """
            SELECT r.id, r.request_number, r.item_type, r.title, r.status, r.contract_ref, r.tender_ref,
                   r.submitted_at, r.decided_at, r.created_at, u.full_name AS requester_name,
                   (SELECT count(*) FROM request_document d WHERE d.request_id = r.id) AS document_count,
                   p.file_number, p.stage AS file_stage,
                   (SELECT s.session_number FROM agenda_item a JOIN committee_session s ON s.id = a.session_id
                     WHERE a.file_id = r.file_id AND s.status IN ('PLANNED', 'IN_PROGRESS')
                     ORDER BY s.scheduled_at LIMIT 1) AS next_session_number,
                   (SELECT min(s.scheduled_at) FROM agenda_item a JOIN committee_session s ON s.id = a.session_id
                     WHERE a.file_id = r.file_id AND s.status IN ('PLANNED', 'IN_PROGRESS')) AS next_session_at
              FROM ptc_request r
              JOIN app_user u ON u.id = r.requester_id
              LEFT JOIN proc_file p ON p.id = r.file_id
            """;

    @Override
    public List<Map<String, Object>> mine() {
        requireRequester();
        return Rows.camel(jdbc.queryForList(LIST_SQL + " WHERE r.requester_id = ? ORDER BY r.created_at DESC",
                access.userId()));
    }

    @Override
    public List<Map<String, Object>> inbox(String status) {
        if (!canReview()) {
            throw ApiException.forbidden("forbidden", "You are not allowed to do this");
        }
        RequestStatus wanted = Enums.parse(RequestStatus.class, status);
        if (status != null && (wanted == null || wanted == RequestStatus.DRAFT)) {
            return List.of();
        }
        if (wanted == null) {
            return Rows.camel(jdbc.queryForList(LIST_SQL
                    + " WHERE r.status <> 'DRAFT' ORDER BY (r.status = 'SUBMITTED') DESC, r.submitted_at DESC"));
        }
        return Rows.camel(jdbc.queryForList(LIST_SQL + " WHERE r.status = ? ORDER BY r.submitted_at DESC", wanted.name()));
    }

    @Override
    public Map<String, Object> detail(UUID id) {
        PtcRequest r = readable(id);
        Map<String, Object> out = new LinkedHashMap<>(Rows.camel(jdbc.queryForMap("""
                SELECT r.id, r.request_number, r.item_type, r.title, r.description, r.contract_ref, r.tender_ref,
                       r.status, r.declared_at, r.submitted_at, r.decided_at, r.decision_note, r.created_at, r.version,
                       u.full_name AS requester_name, u.email AS requester_email, u.job_title AS requester_job_title,
                       d.full_name AS decided_by_name
                  FROM ptc_request r
                  JOIN app_user u ON u.id = r.requester_id
                  LEFT JOIN app_user d ON d.id = r.decided_by
                 WHERE r.id = ?
                """, id)));
        out.put("mine", r.requesterId.equals(access.userId()));
        out.put("documents", documentRows(id));
        out.put("file", fileProgress(r));
        return out;
    }

    private List<Map<String, Object>> documentRows(UUID requestId) {
        return Rows.camel(jdbc.queryForList("""
                SELECT id, file_name, content_type, size_bytes, uploaded_at
                  FROM request_document WHERE request_id = ? ORDER BY uploaded_at
                """, requestId));
    }

    /** What the requester may follow: the file's number and stage, and the next session it is on. */
    private Map<String, Object> fileProgress(PtcRequest r) {
        if (r.fileId == null) {
            return null;
        }
        Map<String, Object> f = new LinkedHashMap<>(Rows.camel(jdbc.queryForMap("""
                SELECT p.id, p.file_number, p.stage, p.closed_at, c.code AS committee_code
                  FROM proc_file p JOIN committee c ON c.id = p.committee_id WHERE p.id = ?
                """, r.fileId)));
        List<Map<String, Object>> next = Rows.camel(jdbc.queryForList("""
                SELECT s.session_number, s.scheduled_at
                  FROM agenda_item a JOIN committee_session s ON s.id = a.session_id
                 WHERE a.file_id = ? AND s.status IN ('PLANNED', 'IN_PROGRESS')
                 ORDER BY s.scheduled_at LIMIT 1
                """, r.fileId));
        f.put("nextSession", next.isEmpty() ? null : next.get(0));
        return f;
    }

    // ------------------------------------------------------------ requester

    private void apply(PtcRequest r, RequestInput in) {
        List<String> types = new ArrayList<>();
        config.snapshot().get(ConfigKeys.REQUEST_TYPES).forEach(n -> types.add(n.asText()));
        if (in.itemType() == null || !types.contains(in.itemType())) {
            throw ApiException.badRequest("request.invalid_type", "Choose a type of request", Map.of("allowed", types));
        }
        if (blank(in.title()) || blank(in.description())) {
            throw ApiException.badRequest("request.invalid", "Give a title and describe the request");
        }
        if (NEEDS_CONTRACT.contains(in.itemType()) && blank(in.contractRef())) {
            throw ApiException.badRequest("request.contract_ref_required", "Give the reference of the contract concerned");
        }
        r.itemType = in.itemType();
        r.title = in.title().trim();
        r.description = in.description().trim();
        r.contractRef = blank(in.contractRef()) ? null : in.contractRef().trim();
        r.tenderRef = blank(in.tenderRef()) ? null : in.tenderRef().trim();
    }

    @Override
    @Transactional
    public PtcRequest create(RequestInput in) {
        requireRequester();
        PtcRequest r = new PtcRequest();
        apply(r, in);
        int year = Time.today().getYear();
        r.requestNumber = "REQ-%d/%04d".formatted(year, numbering.next("REQ:" + year));
        r.requesterId = access.userId();
        requests.saveAndFlush(r);
        audit.created("ptc_request", r.id, r);
        return r;
    }

    @Override
    @Transactional
    public PtcRequest update(UUID id, RequestInput in) {
        PtcRequest r = editable(id);
        if (in.version() != null && !in.version().equals(r.version)) {
            throw ApiException.conflict("concurrent_modification", "Reload and try again");
        }
        var before = audit.snapshot(r);
        apply(r, in);
        requests.saveAndFlush(r);
        audit.updated("ptc_request", r.id, before, r);
        return r;
    }

    @Override
    @Transactional
    public PtcRequest submit(UUID id, boolean declarationConfirmed) {
        PtcRequest r = editable(id);
        if (!declarationConfirmed) {
            throw ApiException.badRequest("request.declaration_required",
                    "Confirm that the documents contain no bid documents, bidder submissions or prices");
        }
        int min = config.snapshot().getInt(ConfigKeys.REQUEST_MIN_DOCUMENTS);
        if (documents.countByRequestId(r.id) < min) {
            throw ApiException.rule("request.documents_required", "Attach the supporting documents before submitting",
                    Map.of("min", min));
        }
        var before = audit.snapshot(r);
        Instant now = Instant.now();
        r.status = RequestStatus.SUBMITTED;
        r.declaredAt = now;
        r.submittedAt = now;
        requests.saveAndFlush(r);
        audit.record("SUBMIT_REQUEST", "ptc_request", r.id, before, r);
        String requester = users.findById(r.requesterId).map(u -> u.fullName).orElse("");
        for (UUID officer : jdbc.queryForList("""
                SELECT DISTINCT m.user_id FROM committee_membership m JOIN committee c ON c.id = m.committee_id
                 WHERE m.status = 'ACTIVE' AND m.role <> 'MEMBER' AND c.status = 'ACTIVE'
                """, UUID.class)) {
            notifications.notify(officer, "REQUEST_SUBMITTED", List.of(r.requestNumber, r.title, requester),
                    "/requests/" + r.id, "ptc_request", r.id, "REQUEST_SUBMITTED:" + r.id + ":" + now.toEpochMilli());
        }
        return r;
    }

    @Override
    @Transactional
    public PtcRequest withdraw(UUID id) {
        PtcRequest r = own(id);
        if (r.status == RequestStatus.ACCEPTED || r.status == RequestStatus.WITHDRAWN) {
            throw ApiException.rule("request.not_withdrawable", "This request can no longer be withdrawn");
        }
        var before = audit.snapshot(r);
        r.status = RequestStatus.WITHDRAWN;
        requests.saveAndFlush(r);
        audit.record("WITHDRAW_REQUEST", "ptc_request", r.id, before, r);
        return r;
    }

    @Override
    @Transactional
    public RequestDocument upload(UUID id, String fileName, String contentType, byte[] content) {
        PtcRequest r = editable(id);
        ConfigSnapshot cfg = config.snapshot();
        int max = cfg.getInt(ConfigKeys.REQUEST_MAX_DOCUMENTS);
        if (documents.countByRequestId(r.id) >= max) {
            throw ApiException.rule("request.too_many_documents", "A request takes at most " + max + " documents",
                    Map.of("max", max));
        }
        long maxBytes = cfg.get(ConfigKeys.REQUEST_MAX_DOCUMENT_BYTES).asLong();
        if (content == null || content.length == 0) {
            throw ApiException.badRequest("request.document_empty", "The document is empty");
        }
        if (content.length > maxBytes) {
            throw ApiException.rule("request.document_too_large", "The document is too large",
                    Map.of("maxMb", maxBytes / (1024 * 1024)));
        }
        List<String> types = cfg.getStrings(ConfigKeys.REQUEST_DOCUMENT_TYPES);
        if (contentType == null || !types.contains(contentType) || !DocumentSignatures.matches(contentType, content)) {
            throw ApiException.rule("request.document_type", "Attach PDF, Word, Excel or image documents only");
        }
        RequestDocument d = new RequestDocument();
        d.requestId = r.id;
        d.fileName = safeName(fileName);
        d.contentType = contentType;
        d.sizeBytes = content.length;
        d.sha256 = sha256(content);
        d.content = content;
        d.uploadedBy = access.userId();
        documents.saveAndFlush(d);
        audit.record("UPLOAD_DOCUMENT", "request_document", d.id, null,
                Map.of("requestId", r.id, "fileName", d.fileName, "sizeBytes", d.sizeBytes, "sha256", d.sha256));
        return d;
    }

    @Override
    @Transactional
    public void deleteDocument(UUID id, UUID documentId) {
        PtcRequest r = editable(id);
        RequestDocument d = documents.findById(documentId).filter(x -> x.requestId.equals(r.id))
                .orElseThrow(() -> ApiException.notFound("Document"));
        documents.delete(d);
        audit.record("DELETE", "request_document", d.id,
                Map.of("requestId", r.id, "fileName", d.fileName, "sha256", d.sha256), null);
    }

    @Override
    public RequestDocument document(UUID id, UUID documentId) {
        PtcRequest r = readable(id);
        return documents.findById(documentId).filter(x -> x.requestId.equals(r.id))
                .orElseThrow(() -> ApiException.notFound("Document"));
    }

    // -------------------------------------------------------------- officers

    @Override
    @Transactional
    public PtcRequest accept(UUID id, AcceptRequest in) {
        PtcRequest r = requests.findById(id).filter(x -> x.status != RequestStatus.DRAFT)
                .orElseThrow(() -> ApiException.notFound("Request"));
        if (in.committeeId() == null) {
            throw ApiException.badRequest("request.committee_required", "Choose the committee that will examine it");
        }
        access.requireOfficer(in.committeeId());
        if (r.status != RequestStatus.SUBMITTED) {
            throw ApiException.conflict("request.decided", "This request has already been decided");
        }
        boolean referral = "H".equals(r.itemType);
        ProcFile f = files.create(new FileRequest(in.committeeId(), r.itemType, r.tenderRef, in.contractId(),
                blank(in.title()) ? r.title : in.title(), null, null, in.procurementOfficerId(),
                Time.localDate(r.submittedAt), in.targetDate(),
                referral ? r.requestNumber : null, referral ? r.description : null, null, null));
        var before = audit.snapshot(r);
        r.status = RequestStatus.ACCEPTED;
        r.committeeId = in.committeeId();
        r.fileId = f.id;
        r.decidedBy = access.userId();
        r.decidedAt = Instant.now();
        requests.saveAndFlush(r);
        audit.record("ACCEPT_REQUEST", "ptc_request", r.id, before, r);
        notifications.notify(r.requesterId, "REQUEST_ACCEPTED", List.of(r.requestNumber, f.fileNumber),
                "/requests/" + r.id, "ptc_request", r.id, "REQUEST_ACCEPTED:" + r.id);
        return r;
    }

    @Override
    @Transactional
    public PtcRequest schedule(UUID id, ScheduleRequest in) {
        if (in.sessionId() == null) {
            throw ApiException.badRequest("request.session_required", "Choose the session");
        }
        UUID committeeId = jdbc.queryForList("SELECT committee_id FROM committee_session WHERE id = ?", UUID.class, in.sessionId())
                .stream().findFirst().orElseThrow(() -> ApiException.notFound("Session"));
        PtcRequest r = accept(id, new AcceptRequest(committeeId, in.contractId(), in.procurementOfficerId(), null, null));
        agenda.add(in.sessionId(), new AddItem(r.fileId, r.itemType, null, null, in.activities()));
        return r;
    }

    @Override
    @Transactional
    public PtcRequest returnToRequester(UUID id, String reason) {
        requireOfficerAnywhere();
        PtcRequest r = requests.findById(id).filter(x -> x.status != RequestStatus.DRAFT)
                .orElseThrow(() -> ApiException.notFound("Request"));
        if (r.status != RequestStatus.SUBMITTED) {
            throw ApiException.conflict("request.decided", "This request has already been decided");
        }
        if (blank(reason)) {
            throw ApiException.badRequest("request.reason_required", "Say why the request is returned");
        }
        var before = audit.snapshot(r);
        r.status = RequestStatus.RETURNED;
        r.decisionNote = reason.trim();
        r.decidedBy = access.userId();
        r.decidedAt = Instant.now();
        requests.saveAndFlush(r);
        audit.record("RETURN_REQUEST", "ptc_request", r.id, before, r);
        notifications.notify(r.requesterId, "REQUEST_RETURNED", List.of(r.requestNumber, r.decisionNote),
                "/requests/" + r.id, "ptc_request", r.id, "REQUEST_RETURNED:" + r.id + ":" + r.decidedAt.toEpochMilli());
        return r;
    }

    @Override
    public Map<String, Object> forFile(UUID fileId) {
        fileAccess.read(fileId);
        return requests.findByFileId(fileId).map(r -> {
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("id", r.id);
            out.put("requestNumber", r.requestNumber);
            out.put("requesterName", users.findById(r.requesterId).map(u -> u.fullName).orElse(null));
            out.put("submittedAt", r.submittedAt);
            out.put("description", r.description);
            out.put("documents", documentRows(r.id));
            return out;
        }).orElse(null);
    }

    // --------------------------------------------------------------- helpers

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }

    /** The last path segment, without control characters, at most 200 characters. */
    static String safeName(String name) {
        String n = name == null ? "" : name.replace('\\', '/');
        n = n.substring(n.lastIndexOf('/') + 1).replaceAll("[\\p{Cntrl}\"]", "").trim();
        if (n.isEmpty()) {
            n = "document";
        }
        return n.length() > 200 ? n.substring(n.length() - 200) : n;
    }

    private static String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
