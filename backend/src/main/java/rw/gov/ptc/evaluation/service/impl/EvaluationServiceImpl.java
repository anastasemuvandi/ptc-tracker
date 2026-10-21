package rw.gov.ptc.evaluation.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import rw.gov.ptc.audit.service.AuditService;
import rw.gov.ptc.calendar.service.WorkingDayCalendar;
import rw.gov.ptc.common.ApiException;
import rw.gov.ptc.common.Time;
import rw.gov.ptc.config.component.ConfigKeys;
import rw.gov.ptc.config.dto.ConfigSnapshot;
import rw.gov.ptc.config.service.ConfigService;
import rw.gov.ptc.evaluation.component.FileAccess;
import rw.gov.ptc.evaluation.component.FileFactory;
import rw.gov.ptc.evaluation.component.IssuedDecisions;
import rw.gov.ptc.evaluation.component.StageCompletedEvent;
import rw.gov.ptc.evaluation.dto.AbnormalInput;
import rw.gov.ptc.evaluation.dto.BidderInput;
import rw.gov.ptc.evaluation.dto.ClarificationInput;
import rw.gov.ptc.evaluation.dto.CureInput;
import rw.gov.ptc.evaluation.dto.CureItem;
import rw.gov.ptc.evaluation.dto.DiligenceInput;
import rw.gov.ptc.evaluation.entity.AbnormalPriceCheck;
import rw.gov.ptc.evaluation.entity.AdminCureRequest;
import rw.gov.ptc.evaluation.entity.BidderResult;
import rw.gov.ptc.evaluation.entity.Clarification;
import rw.gov.ptc.evaluation.entity.DueDiligence;
import rw.gov.ptc.evaluation.entity.Evaluation;
import rw.gov.ptc.evaluation.entity.EvaluationStage;
import rw.gov.ptc.evaluation.enums.EvaluationStageStatus;
import rw.gov.ptc.evaluation.repository.AbnormalPriceCheckRepository;
import rw.gov.ptc.evaluation.repository.AdminCureRequestRepository;
import rw.gov.ptc.evaluation.repository.BidderResultRepository;
import rw.gov.ptc.evaluation.repository.ClarificationRepository;
import rw.gov.ptc.evaluation.repository.DueDiligenceRepository;
import rw.gov.ptc.evaluation.repository.EvaluationRepository;
import rw.gov.ptc.evaluation.repository.EvaluationStageRepository;
import rw.gov.ptc.evaluation.service.EvaluationService;
import rw.gov.ptc.file.entity.ProcFile;
import rw.gov.ptc.file.repository.ProcFileRepository;
import rw.gov.ptc.institution.entity.AppUser;
import rw.gov.ptc.institution.repository.AppUserRepository;
import rw.gov.ptc.tenancy.TenantContext;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class EvaluationServiceImpl implements EvaluationService {

    /** Stage data must never carry scores or prices. */
    private static final List<String> FORBIDDEN_KEY_PARTS = List.of("score", "price", "amount", "cost", "points");

    /** Reasons that only restate the verdict; the committee must say which requirement was not met and why. */
    private static final Set<String> VAGUE_REASONS = Set.of("noncompliant", "notcompliant", "nonresponsive",
            "notresponsive", "failed", "fail", "rejected", "notqualified", "insufficient", "na", "none",
            "doesnotcomply", "didnotcomply");

    private static final int MIN_REASON_LENGTH = 15;
    private static final Set<String> BIDDER_STAGES = Set.of("A1", "A2", "A3");
    private static final Set<String> CLARIFICATION_STAGES = Set.of("A1", "A2", "A3");
    static final Set<String> ALL_BIDS_GROUNDS = Set.of("ALL_ABNORMAL", "ALL_OVER_BUDGET", "ALL_NON_RESPONSIVE");

    private final FileAccess fileAccess;
    private final ProcFileRepository files;
    private final EvaluationRepository evaluations;
    private final EvaluationStageRepository stages;
    private final BidderResultRepository bidders;
    private final AbnormalPriceCheckRepository abnormal;
    private final AdminCureRequestRepository cures;
    private final DueDiligenceRepository diligence;
    private final ClarificationRepository clarifications;
    private final ConfigService config;
    private final WorkingDayCalendar calendar;
    private final AuditService audit;
    private final FileFactory factory;
    private final IssuedDecisions decisions;
    private final AppUserRepository users;
    private final ObjectMapper mapper;
    private final ApplicationEventPublisher events;

    public EvaluationServiceImpl(FileAccess fileAccess, ProcFileRepository files, EvaluationRepository evaluations,
                             EvaluationStageRepository stages, BidderResultRepository bidders,
                             AbnormalPriceCheckRepository abnormal, AdminCureRequestRepository cures,
                             DueDiligenceRepository diligence, ClarificationRepository clarifications,
                             ConfigService config, WorkingDayCalendar calendar, AuditService audit,
                             FileFactory factory, IssuedDecisions decisions, AppUserRepository users,
                             ObjectMapper mapper, ApplicationEventPublisher events) {
        this.events = events;
        this.fileAccess = fileAccess;
        this.files = files;
        this.evaluations = evaluations;
        this.stages = stages;
        this.bidders = bidders;
        this.abnormal = abnormal;
        this.cures = cures;
        this.diligence = diligence;
        this.clarifications = clarifications;
        this.config = config;
        this.calendar = calendar;
        this.audit = audit;
        this.factory = factory;
        this.decisions = decisions;
        this.users = users;
        this.mapper = mapper;
    }

    // ================================================================ context

    /** Everything the blocker rules look at, loaded once per request. */
    record Ctx(ProcFile file, Evaluation evaluation, List<EvaluationStage> stages, List<BidderResult> results,
               List<AbnormalPriceCheck> abnormal, List<AdminCureRequest> cures, List<DueDiligence> diligence,
               List<Clarification> clarifications, Optional<Map<String, Object>> issuedDecision) {

        Optional<EvaluationStage> stage(String code) {
            return stages.stream().filter(s -> s.stageCode.equals(code)).findFirst();
        }

        List<BidderResult> resultsAt(EvaluationStage s) {
            return results.stream().filter(r -> r.stageId.equals(s.id)).toList();
        }
    }

    private Ctx load(ProcFile f, Evaluation ev) {
        return new Ctx(f, ev, stages.findByEvaluationIdOrderBySequenceAsc(ev.id),
                bidders.findByFileIdOrderByRecordedAtAsc(f.id), abnormal.findByFileIdOrderByRecordedAtAsc(f.id),
                cures.findByFileIdOrderByRecordedAtAsc(f.id), diligence.findByFileIdOrderByRecordedAtAsc(f.id),
                clarifications.findByFileIdOrderByRecordedAtAsc(f.id), decisions.latestIssued(f.id));
    }

    /** Opens the evaluation on first write, freezing the stage definitions in force. */
    Evaluation ensure(ProcFile f) {
        FileAccess.requireType(f, "A");
        return evaluations.findByFileId(f.id).orElseGet(() -> {
            ConfigSnapshot snapshot = config.snapshot();
            Evaluation ev = new Evaluation();
            ev.fileId = f.id;
            ev.configSnapshot = snapshot.json();
            evaluations.save(ev);
            for (JsonNode def : stageDefinitions(snapshot)) {
                EvaluationStage s = new EvaluationStage();
                s.evaluationId = ev.id;
                s.fileId = f.id;
                s.stageCode = def.path("code").asText();
                s.sequence = def.path("sequence").asInt();
                s.data = mapper.createObjectNode();
                stages.save(s);
            }
            audit.created("evaluation", ev.id, Map.of("fileId", f.id, "stages", stageDefinitions(snapshot)));
            if (f.openedAt == null) {
                var before = audit.snapshot(f);
                f.openedAt = Instant.now();
                files.save(f);
                audit.record("FILE_OPENED", "proc_file", f.id, before, f);
            }
            return ev;
        });
    }

    private static List<JsonNode> stageDefinitions(ConfigSnapshot snapshot) {
        List<JsonNode> defs = new ArrayList<>();
        snapshot.get(ConfigKeys.EVALUATION_STAGES).forEach(defs::add);
        defs.sort(Comparator.comparingInt(d -> d.path("sequence").asInt()));
        return defs;
    }

    // ================================================================ blockers

    static Map<String, Object> blocker(String code, Object... kv) {
        Map<String, Object> params = new LinkedHashMap<>();
        for (int i = 0; i + 1 < kv.length; i += 2) {
            params.put((String) kv[i], kv[i + 1]);
        }
        return Map.of("code", "evaluation.blocker." + code, "params", params);
    }

    List<Map<String, Object>> startBlockers(Ctx ctx, EvaluationStage s) {
        List<Map<String, Object>> out = new ArrayList<>();
        if (ctx.evaluation().criteriaLockedAt == null) {
            out.add(blocker("criteria_not_locked"));
        }
        ctx.stages().stream()
                .filter(o -> o.sequence < s.sequence)
                .max(Comparator.comparingInt(o -> o.sequence))
                .filter(prev -> prev.status != EvaluationStageStatus.COMPLETED)
                .ifPresent(prev -> out.add(blocker("previous_stage_incomplete", "stage", prev.stageCode)));
        if ("A4".equals(s.stageCode)) {
            // Administrative compliance of the first-ranked bidder comes after
            // technical and financial evaluation — never before.
            List<String> missing = new ArrayList<>();
            for (String code : List.of("A2", "A3")) {
                ctx.stage(code).filter(o -> o.status != EvaluationStageStatus.COMPLETED).ifPresent(o -> missing.add(code));
            }
            if (!missing.isEmpty()) {
                out.add(blocker("admin_after_technical_financial", "stages", missing));
            }
        }
        return out;
    }

    List<Map<String, Object>> completeBlockers(Ctx ctx, EvaluationStage s) {
        List<Map<String, Object>> out = new ArrayList<>();
        if (s.status != EvaluationStageStatus.IN_PROGRESS) {
            out.add(blocker("stage_not_started"));
            return out;
        }
        JsonNode d = s.data == null ? mapper.createObjectNode() : s.data;
        List<BidderResult> results = ctx.resultsAt(s);
        switch (s.stageCode) {
            case "A1" -> {
                if (results.isEmpty()) out.add(blocker("no_bidder_results"));
            }
            case "A2" -> {
                if (results.isEmpty()) out.add(blocker("no_bidder_results"));
                if (!d.path("evaluatedCount").isInt() || !d.path("passingCount").isInt()) {
                    out.add(blocker("counts_missing"));
                } else {
                    long pass = results.stream().filter(r -> "PASS".equals(r.result)).count();
                    if (d.path("evaluatedCount").asInt() != results.size() || d.path("passingCount").asInt() != pass) {
                        out.add(blocker("counts_inconsistent", "recordedBidders", results.size(), "recordedPassing", pass));
                    }
                }
                if (!resourcePersonConfirmed(d)) out.add(blocker("resource_person_unconfirmed"));
                if (rankingMissing(results)) out.add(blocker("ranking_missing"));
            }
            case "A3" -> {
                if (results.isEmpty()) out.add(blocker("no_bidder_results"));
                if (!d.path("arithmeticCheckDone").asBoolean(false)) out.add(blocker("arithmetic_check_missing"));
                if (!d.path("taxesIncludedInComparison").isBoolean()) out.add(blocker("tax_basis_missing"));
                long pending = ctx.abnormal().stream().filter(a -> "PENDING".equals(a.decision)).count();
                if (pending > 0) out.add(blocker("abnormal_price_pending", "count", pending));
                if (d.hasNonNull("allBidsFlag") && !d.hasNonNull("rejectionOfAllFileId")) {
                    out.add(blocker("rejection_of_all_not_raised", "ground", d.path("allBidsFlag").asText()));
                }
                if (!d.hasNonNull("allBidsFlag") && rankingMissing(results)) out.add(blocker("ranking_missing"));
            }
            case "A4" -> {
                long pending = ctx.cures().stream().filter(c -> "PENDING".equals(c.outcome)).count();
                if (pending > 0) out.add(blocker("cure_pending", "count", pending));
                if (ctx.cures().isEmpty() && !d.path("allRequirementsMet").asBoolean(false)) {
                    out.add(blocker("cure_not_recorded"));
                }
            }
            case "A5" -> {
                if (ctx.diligence().isEmpty()) out.add(blocker("due_diligence_missing"));
            }
            case "A6" -> {
                if (ctx.issuedDecision().isEmpty()) out.add(blocker("recommendation_not_issued"));
            }
            default -> {
                // custom stage codes from configuration: no built-in rules
            }
        }
        if (CLARIFICATION_STAGES.contains(s.stageCode)) {
            long open = ctx.clarifications().stream().filter(c -> "PENDING".equals(c.effect)).count();
            if (open > 0) out.add(blocker("clarification_pending", "count", open));
        }
        return out;
    }

    private static boolean resourcePersonConfirmed(JsonNode d) {
        JsonNode rp = d.path("resourcePerson");
        if (!rp.isObject() || rp.path("name").asText("").isBlank()) {
            return true; // no resource person supported the committee
        }
        return !rp.path("institution").asText("").isBlank()
                && rp.path("noDecisionRole").asBoolean(false)
                && rp.path("noInterest").asBoolean(false);
    }

    private static boolean rankingMissing(List<BidderResult> results) {
        return results.stream().anyMatch(r -> "PASS".equals(r.result) && r.rank == null);
    }

    // ================================================================ view

    @Override
    @Transactional
    public Map<String, Object> progress(UUID fileId) {
        ProcFile f = fileAccess.read(fileId);
        FileAccess.requireType(f, "A");
        Optional<Evaluation> ev = evaluations.findByFileId(f.id);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("fileId", f.id);
        out.put("fileNumber", f.fileNumber);
        out.put("title", f.title);
        out.put("tenderRef", f.tenderRef);
        out.put("stage", f.stage);
        out.put("bidValidity", bidValidity(f));
        out.put("clarificationReminder", "evaluation.clarification_reminder");
        out.put("sequenceNotice", "evaluation.sequence_notice");

        ConfigSnapshot current = config.snapshot();
        Map<String, Object> cfg = new LinkedHashMap<>();
        cfg.put("preliminaryRequirements", current.getStrings(ConfigKeys.PRELIMINARY_REQUIREMENTS));
        cfg.put("cureItems", current.getStrings(ConfigKeys.CURE_ITEMS));
        cfg.put("cureWorkingDays", current.getInt(ConfigKeys.CURE_WORKING_DAYS));
        cfg.put("bidSecurityDeclarationThreshold", current.get(ConfigKeys.BID_SECURITY_DECLARATION_THRESHOLD));
        cfg.put("allBidsGrounds", ALL_BIDS_GROUNDS.stream().sorted().toList());
        out.put("config", cfg);

        if (ev.isEmpty()) {
            // Not opened yet: show the stages that would apply, nothing persisted.
            out.put("evaluation", null);
            out.put("criteriaLocked", false);
            List<Map<String, Object>> preview = new ArrayList<>();
            boolean first = true;
            for (JsonNode def : stageDefinitions(current)) {
                Map<String, Object> s = new LinkedHashMap<>();
                s.put("code", def.path("code").asText());
                s.put("sequence", def.path("sequence").asInt());
                s.put("nameKey", def.path("nameKey").asText());
                s.put("status", "NOT_STARTED");
                s.put("nextAction", first ? "START" : "NONE");
                s.put("blockers", first ? List.of(blocker("criteria_not_locked"))
                        : List.of(blocker("previous_stage_incomplete")));
                preview.add(s);
                first = false;
            }
            out.put("stages", preview);
            out.put("currentStage", preview.isEmpty() ? null : preview.get(0).get("code"));
            for (String k : List.of("bidderResults", "abnormalPriceChecks", "cureRequests", "dueDiligence", "clarifications")) {
                out.put(k, List.of());
            }
            out.put("issuedDecision", decisions.latestIssued(f.id).orElse(null));
            return out;
        }

        Evaluation evaluation = ev.get();
        Ctx ctx = load(f, evaluation);
        if (reconcileRecommendationStage(ctx)) {
            ctx = load(f, evaluation);
        }
        Map<UUID, String> names = names(ctx);
        Map<String, String> nameKeys = new LinkedHashMap<>();
        for (JsonNode def : stageDefinitions(config.fromStored(evaluation.configSnapshot))) {
            nameKeys.put(def.path("code").asText(), def.path("nameKey").asText());
        }

        Map<String, Object> e = new LinkedHashMap<>();
        e.put("id", evaluation.id);
        e.put("criteriaLockedAt", evaluation.criteriaLockedAt);
        e.put("criteriaLockedByName", names.get(evaluation.criteriaLockedBy));
        e.put("criteriaDefectFindings", evaluation.criteriaDefectFindings);
        e.put("version", evaluation.version);
        out.put("evaluation", e);
        out.put("criteriaLocked", evaluation.criteriaLockedAt != null);

        List<Map<String, Object>> stageViews = new ArrayList<>();
        String currentStage = null;
        Map<UUID, String> stageCodeById = new LinkedHashMap<>();
        for (EvaluationStage s : ctx.stages()) {
            stageCodeById.put(s.id, s.stageCode);
            Map<String, Object> v = new LinkedHashMap<>();
            v.put("code", s.stageCode);
            v.put("sequence", s.sequence);
            v.put("nameKey", nameKeys.getOrDefault(s.stageCode, "evaluation.stage." + s.stageCode));
            v.put("status", s.status);
            v.put("outcome", s.outcome);
            v.put("data", s.data);
            v.put("startedAt", s.startedAt);
            v.put("completedAt", s.completedAt);
            v.put("completedByName", names.get(s.completedBy));
            v.put("version", s.version);
            switch (s.status) {
                case NOT_STARTED -> {
                    v.put("nextAction", "START");
                    v.put("blockers", startBlockers(ctx, s));
                }
                case IN_PROGRESS -> {
                    v.put("nextAction", "COMPLETE");
                    v.put("blockers", completeBlockers(ctx, s));
                }
                default -> {
                    v.put("nextAction", "NONE");
                    v.put("blockers", List.of());
                }
            }
            if (currentStage == null && s.status != EvaluationStageStatus.COMPLETED) {
                currentStage = s.stageCode;
            }
            stageViews.add(v);
        }
        out.put("stages", stageViews);
        out.put("currentStage", currentStage);
        out.put("complete", currentStage == null);

        out.put("bidderResults", ctx.results().stream().map(r -> {
            Map<String, Object> m = toMap(r);
            m.put("stageCode", stageCodeById.get(r.stageId));
            return m;
        }).toList());
        out.put("abnormalPriceChecks", ctx.abnormal());
        out.put("cureRequests", ctx.cures().stream().map(this::cureView).toList());
        out.put("dueDiligence", ctx.diligence());
        out.put("clarifications", ctx.clarifications());
        out.put("issuedDecision", ctx.issuedDecision().orElse(null));
        return out;
    }

    private Map<UUID, String> names(Ctx ctx) {
        Set<UUID> ids = new HashSet<>();
        ids.add(ctx.evaluation().criteriaLockedBy);
        ctx.stages().forEach(s -> ids.add(s.completedBy));
        ids.remove(null);
        return users.findAllById(ids).stream().collect(Collectors.toMap(u -> u.id, (AppUser u) -> u.fullName));
    }

    private Map<String, Object> toMap(Object entity) {
        @SuppressWarnings("unchecked")
        Map<String, Object> m = mapper.convertValue(entity, LinkedHashMap.class);
        return m;
    }

    Map<String, Object> cureView(AdminCureRequest c) {
        Map<String, Object> m = toMap(c);
        LocalDate today = Time.today();
        boolean pending = "PENDING".equals(c.outcome);
        m.put("workingDaysRemaining", pending ? calendar.workingDaysBetween(today, c.deadline) : null);
        m.put("overdue", pending && today.isAfter(c.deadline));
        m.put("suppliedLate", c.suppliedOn != null && c.suppliedOn.isAfter(c.deadline));
        return m;
    }

    @Override
    public Map<String, Object> bidValidity(ProcFile f) {
        Map<String, Object> out = new LinkedHashMap<>();
        LocalDate expiry = f.bidValidityExpiry;
        out.put("expiry", expiry);
        if (expiry == null) {
            out.put("state", "NOT_SET");
            return out;
        }
        ConfigSnapshot c = config.snapshot();
        LocalDate today = Time.today();
        LocalDate requestDeadline = expiry.minusDays(c.getInt(ConfigKeys.BID_VALIDITY_REGULATOR_LEAD_DAYS));
        long toRequest = ChronoUnit.DAYS.between(today, requestDeadline);
        long toExpiry = ChronoUnit.DAYS.between(today, expiry);
        out.put("regulatorRequestDeadline", requestDeadline);
        out.put("daysToRequestDeadline", toRequest);
        out.put("daysToExpiry", toExpiry);
        String state;
        if (toExpiry < 0) {
            state = "EXPIRED";
        } else if (toExpiry <= c.getInt(ConfigKeys.BID_VALIDITY_ALERT_BEFORE_EXPIRY_DAYS)) {
            state = "EXPIRING";
        } else if (toRequest < 0) {
            state = "REQUEST_OVERDUE";
        } else if (toRequest <= c.getInt(ConfigKeys.BID_VALIDITY_ALERT_BEFORE_REQUEST_DAYS)) {
            state = "REQUEST_DUE_SOON";
        } else {
            state = "OK";
        }
        out.put("state", state);
        return out;
    }

    /** A6 is completed by the issue of the committee's recommendation on the file. */
    private boolean reconcileRecommendationStage(Ctx ctx) {
        Optional<EvaluationStage> a6 = ctx.stage("A6");
        if (a6.isEmpty() || ctx.issuedDecision().isEmpty()) {
            return false;
        }
        EvaluationStage s = a6.get();
        if (s.status == EvaluationStageStatus.COMPLETED) {
            return false;
        }
        boolean earlierDone = ctx.stages().stream()
                .filter(o -> o.sequence < s.sequence).allMatch(o -> o.status == EvaluationStageStatus.COMPLETED);
        if (!earlierDone) {
            return false;
        }
        Map<String, Object> d = ctx.issuedDecision().get();
        var before = audit.snapshot(s);
        Instant now = Instant.now();
        if (s.startedAt == null) {
            s.startedAt = now;
        }
        s.status = EvaluationStageStatus.COMPLETED;
        s.completedAt = d.get("issuedAt") instanceof Instant i ? i : now;
        s.completedBy = (UUID) d.get("issuedBy");
        s.outcome = "Recommendation issued: " + d.get("recommendationCode")
                + (d.get("recommendedBidderRef") == null ? "" : " — " + d.get("recommendedBidderRef"));
        stages.save(s);
        audit.record("EVALUATION_STAGE_COMPLETED", "evaluation_stage", s.id, before, s);
        events.publishEvent(new StageCompletedEvent(s.fileId, s.stageCode));
        return true;
    }

    // ================================================================ criteria lock

    @Override
    @Transactional
    public Map<String, Object> lockCriteria(UUID fileId, boolean confirmed, String findings) {
        ProcFile f = fileAccess.write(fileId);
        if (!confirmed) {
            throw ApiException.badRequest("evaluation.criteria_confirmation_required",
                    "Confirm that only the published criteria will be applied, with nothing added or deleted");
        }
        Evaluation ev = ensure(f);
        if (ev.criteriaLockedAt != null) {
            throw ApiException.conflict("evaluation.criteria_already_locked", "The criteria are already locked");
        }
        var before = audit.snapshot(ev);
        ev.criteriaLockedAt = Instant.now();
        ev.criteriaLockedBy = TenantContext.userOrNull();
        if (findings != null && !findings.isBlank()) {
            ev.criteriaDefectFindings = findings.trim();
        }
        evaluations.save(ev);
        audit.record("EVALUATION_CRITERIA_LOCKED", "evaluation", ev.id, before, ev);
        return progress(fileId);
    }

    @Override
    @Transactional
    public Map<String, Object> recordFindings(UUID fileId, String findings) {
        ProcFile f = fileAccess.write(fileId);
        Evaluation ev = ensure(f);
        var before = audit.snapshot(ev);
        ev.criteriaDefectFindings = findings == null || findings.isBlank() ? null : findings.trim();
        evaluations.save(ev);
        audit.updated("evaluation", ev.id, before, ev);
        return progress(fileId);
    }

    // ================================================================ stages

    private EvaluationStage stageOf(Evaluation ev, String code) {
        return stages.findByEvaluationIdAndStageCode(ev.id, code)
                .orElseThrow(() -> ApiException.notFound("Evaluation stage"));
    }

    private static void requireInProgress(EvaluationStage s) {
        if (s.status == EvaluationStageStatus.COMPLETED) {
            throw ApiException.rule("evaluation.stage_completed", "This stage is completed and can no longer be changed",
                    Map.of("stage", s.stageCode));
        }
        if (s.status != EvaluationStageStatus.IN_PROGRESS) {
            throw ApiException.rule("evaluation.stage_not_in_progress", "Start this stage first",
                    Map.of("stage", s.stageCode));
        }
    }

    private static ApiException blocked(EvaluationStage s, List<Map<String, Object>> blockers) {
        return ApiException.rule("evaluation.stage_blocked", "This stage cannot proceed yet",
                Map.of("stage", s.stageCode, "blockers", blockers));
    }

    @Override
    @Transactional
    public Map<String, Object> startStage(UUID fileId, String code) {
        ProcFile f = fileAccess.write(fileId);
        Evaluation ev = ensure(f);
        EvaluationStage s = stageOf(ev, code);
        if (s.status != EvaluationStageStatus.NOT_STARTED) {
            throw ApiException.conflict("evaluation.stage_already_started", "This stage has already been started");
        }
        List<Map<String, Object>> blockers = startBlockers(load(f, ev), s);
        if (!blockers.isEmpty()) {
            throw blocked(s, blockers);
        }
        var before = audit.snapshot(s);
        s.status = EvaluationStageStatus.IN_PROGRESS;
        s.startedAt = Instant.now();
        stages.save(s);
        audit.record("EVALUATION_STAGE_STARTED", "evaluation_stage", s.id, before, s);
        if (Set.of("RECEIVED", "SCHEDULED").contains(f.stage)) {
            factory.changeStage(f, "UNDER_REVIEW", "Evaluation stage " + code + " started");
        }
        return progress(fileId);
    }

    @Override
    @Transactional
    public Map<String, Object> updateStage(UUID fileId, String code, JsonNode data, String outcome, Long version) {
        ProcFile f = fileAccess.write(fileId);
        Evaluation ev = ensure(f);
        EvaluationStage s = stageOf(ev, code);
        requireInProgress(s);
        FileAccess.checkVersion(version, s.version);
        var before = audit.snapshot(s);
        if (data != null) {
            if (!data.isObject()) {
                throw ApiException.badRequest("evaluation.invalid_data", "Stage data must be an object");
            }
            rejectScores(data, "");
            validateStageData(code, data);
            s.data = data;
        }
        if (outcome != null) {
            s.outcome = outcome.isBlank() ? null : outcome.trim();
        }
        stages.save(s);
        audit.updated("evaluation_stage", s.id, before, s);
        return progress(fileId);
    }

    @Override
    @Transactional
    public Map<String, Object> completeStage(UUID fileId, String code, String outcome) {
        ProcFile f = fileAccess.write(fileId);
        Evaluation ev = ensure(f);
        EvaluationStage s = stageOf(ev, code);
        if (outcome != null && !outcome.isBlank()) {
            s.outcome = outcome.trim();
        }
        if (s.outcome == null || s.outcome.isBlank()) {
            throw ApiException.badRequest("evaluation.outcome_required", "Record the outcome of this stage");
        }
        List<Map<String, Object>> blockers = completeBlockers(load(f, ev), s);
        if (!blockers.isEmpty()) {
            throw blocked(s, blockers);
        }
        var before = audit.snapshot(s);
        s.status = EvaluationStageStatus.COMPLETED;
        s.completedAt = Instant.now();
        s.completedBy = TenantContext.userOrNull();
        stages.save(s);
        audit.record("EVALUATION_STAGE_COMPLETED", "evaluation_stage", s.id, before, s);
        events.publishEvent(new StageCompletedEvent(s.fileId, s.stageCode));
        return progress(fileId);
    }

    /** Recursively refuses keys that would carry scores or prices. */
    static void rejectScores(JsonNode node, String path) {
        if (node.isObject()) {
            Iterator<Map.Entry<String, JsonNode>> it = node.fields();
            while (it.hasNext()) {
                var e = it.next();
                String key = e.getKey().toLowerCase(Locale.ROOT);
                for (String part : FORBIDDEN_KEY_PARTS) {
                    if (key.contains(part)) {
                        throw ApiException.rule("evaluation.scores_not_stored",
                                "Scores, prices and amounts are recorded in Umucyo, not here",
                                Map.of("field", path + e.getKey()));
                    }
                }
                rejectScores(e.getValue(), path + e.getKey() + ".");
            }
        } else if (node.isArray()) {
            for (JsonNode n : node) {
                rejectScores(n, path);
            }
        }
    }

    private static void validateStageData(String code, JsonNode d) {
        if ("A2".equals(code)) {
            for (String k : List.of("evaluatedCount", "passingCount")) {
                if (d.has(k) && !d.get(k).isNull() && (!d.get(k).isInt() || d.get(k).asInt() < 0)) {
                    throw ApiException.badRequest("evaluation.invalid_count", "Counts must be whole numbers", Map.of("field", k));
                }
            }
            if (d.path("evaluatedCount").isInt() && d.path("passingCount").isInt()
                    && d.path("passingCount").asInt() > d.path("evaluatedCount").asInt()) {
                throw ApiException.badRequest("evaluation.invalid_count", "More bidders passing than evaluated");
            }
        }
        if ("A3".equals(code) && d.hasNonNull("allBidsFlag") && !ALL_BIDS_GROUNDS.contains(d.path("allBidsFlag").asText())) {
            throw ApiException.badRequest("evaluation.invalid_all_bids_flag", "Unknown ground",
                    Map.of("allowed", ALL_BIDS_GROUNDS.stream().sorted().toList()));
        }
    }

    // ================================================================ bidder results

    @Override
    @Transactional
    public Map<String, Object> recordBidder(UUID fileId, String code, BidderInput in) {
        ProcFile f = fileAccess.write(fileId);
        Evaluation ev = ensure(f);
        EvaluationStage s = stageOf(ev, code);
        if (!BIDDER_STAGES.contains(code)) {
            throw ApiException.rule("evaluation.no_bidder_results_at_stage", "Per-bidder results are not recorded at this stage",
                    Map.of("stage", code));
        }
        requireInProgress(s);
        if (in.bidderRef() == null || in.bidderRef().isBlank()) {
            throw ApiException.badRequest("evaluation.bidder_ref_required", "Bidder reference is required");
        }
        String ref = in.bidderRef().trim();
        if (!Set.of("PASS", "FAIL").contains(in.result())) {
            throw ApiException.badRequest("evaluation.invalid_result", "Result must be PASS or FAIL");
        }
        Ctx ctx = load(f, ev);
        // A bidder eliminated at an earlier stage is not evaluated further.
        for (EvaluationStage earlier : ctx.stages()) {
            if (earlier.sequence < s.sequence && ctx.resultsAt(earlier).stream()
                    .anyMatch(r -> r.bidderRef.equalsIgnoreCase(ref) && "FAIL".equals(r.result))) {
                throw ApiException.rule("evaluation.bidder_eliminated", "This bidder did not pass an earlier stage",
                        Map.of("bidderRef", ref, "stage", earlier.stageCode));
            }
        }
        if ("FAIL".equals(in.result())) {
            if (in.failedRequirement() == null || in.failedRequirement().isBlank()) {
                throw ApiException.rule("evaluation.requirement_not_met_required",
                        "State which requirement or criterion was not substantially met");
            }
            if ("A1".equals(code)) {
                List<String> allowed = config.fromStored(ev.configSnapshot).has(ConfigKeys.PRELIMINARY_REQUIREMENTS)
                        ? config.fromStored(ev.configSnapshot).getStrings(ConfigKeys.PRELIMINARY_REQUIREMENTS)
                        : config.snapshot().getStrings(ConfigKeys.PRELIMINARY_REQUIREMENTS);
                if (!allowed.contains(in.failedRequirement())) {
                    throw ApiException.badRequest("evaluation.unknown_requirement", "Unknown preliminary requirement",
                            Map.of("allowed", allowed));
                }
            }
            requireSubstantiveReason(in.failureReason());
            if (in.rank() != null) {
                throw ApiException.badRequest("evaluation.rank_for_failed", "Only passing bidders are ranked");
            }
        }
        if (in.rank() != null) {
            if (in.rank() < 1) {
                throw ApiException.badRequest("evaluation.invalid_rank", "Rank starts at 1");
            }
            boolean taken = ctx.resultsAt(s).stream()
                    .anyMatch(r -> !r.bidderRef.equalsIgnoreCase(ref) && in.rank().equals(r.rank));
            if (taken) {
                throw ApiException.rule("evaluation.duplicate_rank", "Another bidder already holds this rank",
                        Map.of("rank", in.rank()));
            }
        }
        BidderResult r = bidders.findByStageIdAndBidderRef(s.id, ref).orElseGet(BidderResult::new);
        boolean created = r.isNew();
        if (!created) {
            FileAccess.checkVersion(in.version(), r.version);
        }
        var before = created ? null : audit.snapshot(r);
        r.stageId = s.id;
        r.fileId = f.id;
        r.bidderRef = ref;
        r.result = in.result();
        r.failedRequirement = "FAIL".equals(in.result()) ? in.failedRequirement().trim() : null;
        r.failureReason = "FAIL".equals(in.result()) ? in.failureReason().trim() : null;
        r.rank = in.rank();
        r.recordedBy = TenantContext.userOrNull();
        r.recordedAt = Instant.now();
        bidders.save(r);
        audit.record(created ? "CREATE" : "UPDATE", "bidder_result", r.id, before, r);
        return progress(fileId);
    }

    /** "Non-compliant" alone is not a reason: say which requirement and why. */
    static void requireSubstantiveReason(String reason) {
        String normalised = reason == null ? "" : reason.toLowerCase(Locale.ROOT).replaceAll("[^a-z]", "");
        if (reason == null || reason.trim().length() < MIN_REASON_LENGTH || VAGUE_REASONS.contains(normalised)) {
            throw ApiException.rule("evaluation.reason_insufficient",
                    "Explain which requirement was not substantially met and why",
                    Map.of("minLength", MIN_REASON_LENGTH));
        }
    }

    // ================================================================ abnormal prices

    @Override
    @Transactional
    public AbnormalPriceCheck createAbnormal(UUID fileId, AbnormalInput in) {
        ProcFile f = fileAccess.write(fileId);
        EvaluationStage a3 = stageOf(ensure(f), "A3");
        requireInProgress(a3);
        AbnormalPriceCheck c = new AbnormalPriceCheck();
        c.fileId = f.id;
        applyAbnormal(c, in);
        abnormal.save(c);
        audit.created("abnormal_price_check", c.id, c);
        return c;
    }

    @Override
    @Transactional
    public AbnormalPriceCheck updateAbnormal(UUID id, AbnormalInput in) {
        AbnormalPriceCheck c = abnormal.findById(id).orElseThrow(() -> ApiException.notFound("Abnormal price check"));
        ProcFile f = fileAccess.write(c.fileId);
        requireInProgress(stageOf(ensure(f), "A3"));
        FileAccess.checkVersion(in.version(), c.version);
        var before = audit.snapshot(c);
        applyAbnormal(c, in);
        abnormal.save(c);
        audit.updated("abnormal_price_check", c.id, before, c);
        return c;
    }

    private void applyAbnormal(AbnormalPriceCheck c, AbnormalInput in) {
        if (in.bidderRef() == null || in.bidderRef().isBlank()) {
            throw ApiException.badRequest("evaluation.bidder_ref_required", "Bidder reference is required");
        }
        if (!Set.of("ABNORMALLY_HIGH", "ABNORMALLY_LOW").contains(in.direction())) {
            throw ApiException.badRequest("evaluation.invalid_direction", "Direction must be ABNORMALLY_HIGH or ABNORMALLY_LOW");
        }
        String decision = in.decision() == null ? "PENDING" : in.decision();
        if (!Set.of("PENDING", "ACCEPTED", "REJECTED").contains(decision)) {
            throw ApiException.badRequest("evaluation.invalid_decision", "Unknown decision");
        }
        if (in.requestDate() != null && in.explanationDate() != null && in.explanationDate().isBefore(in.requestDate())) {
            throw ApiException.badRequest("evaluation.explanation_before_request",
                    "The explanation cannot predate the written request");
        }
        if ("REJECTED".equals(decision)) {
            // Rejection without the written exchange is a procedural defect.
            List<String> missing = new ArrayList<>();
            if (blank(in.requestRef())) missing.add("requestRef");
            if (in.requestDate() == null) missing.add("requestDate");
            if (blank(in.analysis())) missing.add("analysis");
            if (!Boolean.FALSE.equals(in.doubtLifted())) missing.add("doubtLifted");
            if (blank(in.decisionReasons())) missing.add("decisionReasons");
            if (!missing.isEmpty()) {
                throw ApiException.rule("evaluation.abnormal_rejection_without_exchange",
                        "A bid cannot be rejected on abnormal-price grounds without the written request for explanation and the committee's analysis",
                        Map.of("missing", missing));
            }
        }
        if ("ACCEPTED".equals(decision) && blank(in.decisionReasons())) {
            throw ApiException.badRequest("evaluation.decision_reasons_required", "Give the reasons for the decision");
        }
        c.bidderRef = in.bidderRef().trim();
        c.direction = in.direction();
        c.requestRef = trim(in.requestRef());
        c.requestDate = in.requestDate();
        c.explanationSummary = trim(in.explanationSummary());
        c.explanationDate = in.explanationDate();
        c.analysis = trim(in.analysis());
        c.doubtLifted = in.doubtLifted();
        c.decision = decision;
        c.decisionReasons = trim(in.decisionReasons());
        c.recordedBy = TenantContext.userOrNull();
        c.recordedAt = Instant.now();
    }

    // ================================================================ rejection of all bids

    @Override
    @Transactional
    public Map<String, Object> raiseRejectionOfAll(UUID fileId, String ground, String reason) {
        ProcFile f = fileAccess.write(fileId);
        if (!ALL_BIDS_GROUNDS.contains(ground)) {
            throw ApiException.badRequest("evaluation.invalid_all_bids_flag", "Unknown ground",
                    Map.of("allowed", ALL_BIDS_GROUNDS.stream().sorted().toList()));
        }
        if (blank(reason)) {
            throw ApiException.badRequest("evaluation.reason_required", "Give the reason");
        }
        EvaluationStage a3 = stageOf(ensure(f), "A3");
        requireInProgress(a3);
        if (a3.data != null && a3.data.hasNonNull("rejectionOfAllFileId")) {
            throw ApiException.conflict("evaluation.rejection_of_all_already_raised",
                    "A rejection-of-all-bids file has already been raised");
        }
        ProcFile raised = factory.derive(f, "F", "Rejection of all bids — " + f.title,
                "Raised from evaluation of " + f.fileNumber + ": " + ground);
        var before = audit.snapshot(a3);
        ObjectNode data = a3.data == null ? mapper.createObjectNode() : ((ObjectNode) a3.data).deepCopy();
        data.put("allBidsFlag", ground);
        data.put("allBidsReason", reason.trim());
        data.put("rejectionOfAllFileId", raised.id.toString());
        data.put("rejectionOfAllFileNumber", raised.fileNumber);
        a3.data = data;
        stages.save(a3);
        audit.record("REJECTION_OF_ALL_RAISED", "evaluation_stage", a3.id, before, a3);
        return Map.of("fileId", raised.id, "fileNumber", raised.fileNumber);
    }

    // ================================================================ cure requests (A4)

    @Override
    @Transactional
    public Map<String, Object> createCure(UUID fileId, CureInput in) {
        ProcFile f = fileAccess.write(fileId);
        Evaluation ev = ensure(f);
        requireInProgress(stageOf(ev, "A4"));
        if (blank(in.bidderRef()) || in.issuedOn() == null || in.outstandingItems() == null
                || in.outstandingItems().isEmpty()) {
            throw ApiException.badRequest("evaluation.cure_invalid",
                    "Bidder, issue date and at least one outstanding requirement are required");
        }
        Ctx ctx = load(f, ev);
        String ref = in.bidderRef().trim();
        ctx.stage("A3").flatMap(a3 -> ctx.resultsAt(a3).stream().filter(r -> Integer.valueOf(1).equals(r.rank)).findFirst())
                .filter(first -> !first.bidderRef.equalsIgnoreCase(ref))
                .ifPresent(first -> {
                    throw ApiException.rule("evaluation.cure_not_first_ranked",
                            "Administrative compliance is checked for the first-ranked bidder only",
                            Map.of("firstRanked", first.bidderRef));
                });
        int days = config.snapshot().getInt(ConfigKeys.CURE_WORKING_DAYS);
        AdminCureRequest c = new AdminCureRequest();
        c.fileId = f.id;
        c.bidderRef = ref;
        c.outstandingItems = items(in.outstandingItems());
        c.issuedOn = in.issuedOn();
        c.workingDays = days;
        c.deadline = calendar.addWorkingDays(in.issuedOn(), days);
        c.recordedBy = TenantContext.userOrNull();
        cures.save(c);
        audit.created("admin_cure_request", c.id, c);
        return cureView(c);
    }

    @Override
    @Transactional
    public Map<String, Object> updateCure(UUID id, CureInput in) {
        AdminCureRequest c = cures.findById(id).orElseThrow(() -> ApiException.notFound("Cure request"));
        ProcFile f = fileAccess.write(c.fileId);
        requireInProgress(stageOf(ensure(f), "A4"));
        FileAccess.checkVersion(in.version(), c.version);
        var before = audit.snapshot(c);
        if (in.outstandingItems() != null && !in.outstandingItems().isEmpty()) {
            c.outstandingItems = items(in.outstandingItems());
        }
        if (in.suppliedSummary() != null) c.suppliedSummary = trim(in.suppliedSummary());
        if (in.suppliedOn() != null) c.suppliedOn = in.suppliedOn();
        if (in.outcomeNote() != null) c.outcomeNote = trim(in.outcomeNote());
        if (in.outcome() != null) {
            if (!Set.of("PENDING", "COMPLIED", "NOT_COMPLIED").contains(in.outcome())) {
                throw ApiException.badRequest("evaluation.invalid_outcome", "Unknown outcome");
            }
            if ("COMPLIED".equals(in.outcome())) {
                boolean allSupplied = true;
                for (JsonNode item : c.outstandingItems) {
                    allSupplied &= item.path("supplied").asBoolean(false);
                }
                if (!allSupplied) {
                    throw ApiException.rule("evaluation.cure_items_outstanding",
                            "Every outstanding requirement must be marked supplied");
                }
            }
            if (!"PENDING".equals(in.outcome()) && blank(c.outcomeNote)) {
                throw ApiException.badRequest("evaluation.outcome_note_required", "Record the outcome");
            }
            c.outcome = in.outcome();
        }
        cures.save(c);
        audit.updated("admin_cure_request", c.id, before, c);
        return cureView(c);
    }

    private JsonNode items(List<CureItem> items) {
        var arr = mapper.createArrayNode();
        Set<String> seen = new HashSet<>();
        for (CureItem i : items) {
            if (blank(i.code()) || !seen.add(i.code())) {
                throw ApiException.badRequest("evaluation.cure_invalid", "Each requirement needs a distinct code");
            }
            ObjectNode n = arr.addObject();
            n.put("code", i.code());
            n.put("label", i.label());
            n.put("supplied", Boolean.TRUE.equals(i.supplied()));
            n.put("suppliedOn", i.suppliedOn() == null ? null : i.suppliedOn().toString());
            n.put("note", i.note());
        }
        return arr;
    }

    // ================================================================ due diligence (A5)

    @Override
    @Transactional
    public DueDiligence createDiligence(UUID fileId, DiligenceInput in) {
        ProcFile f = fileAccess.write(fileId);
        requireInProgress(stageOf(ensure(f), "A5"));
        DueDiligence d = new DueDiligence();
        d.fileId = f.id;
        applyDiligence(d, in);
        diligence.save(d);
        audit.created("due_diligence", d.id, d);
        return d;
    }

    @Override
    @Transactional
    public DueDiligence updateDiligence(UUID id, DiligenceInput in) {
        DueDiligence d = diligence.findById(id).orElseThrow(() -> ApiException.notFound("Due diligence"));
        ProcFile f = fileAccess.write(d.fileId);
        requireInProgress(stageOf(ensure(f), "A5"));
        FileAccess.checkVersion(in.version(), d.version);
        var before = audit.snapshot(d);
        applyDiligence(d, in);
        diligence.save(d);
        audit.updated("due_diligence", d.id, before, d);
        return d;
    }

    private void applyDiligence(DueDiligence d, DiligenceInput in) {
        if (blank(in.bidderRef()) || blank(in.performedByName()) || in.performedOn() == null || blank(in.itemsVerified())) {
            throw ApiException.badRequest("evaluation.due_diligence_invalid",
                    "Record the bidder, who verified, when, and what was verified");
        }
        if (!Set.of("CONFIRMED", "FALSE", "MATERIALLY_CONFUSING", "INCOMPLETE").contains(in.result())) {
            throw ApiException.badRequest("evaluation.invalid_result", "Unknown result");
        }
        boolean disqualified = Boolean.TRUE.equals(in.disqualified());
        if (!"CONFIRMED".equals(in.result())) {
            // False, materially confusing or incomplete information disqualifies.
            if (!disqualified || blank(in.disqualificationGround())) {
                throw ApiException.rule("evaluation.due_diligence_disqualification_required",
                        "Record the disqualification and its ground");
            }
        } else if (disqualified) {
            throw ApiException.badRequest("evaluation.due_diligence_inconsistent",
                    "A confirmed verification does not disqualify");
        }
        d.bidderRef = in.bidderRef().trim();
        d.performedByName = in.performedByName().trim();
        d.performedOn = in.performedOn();
        d.itemsVerified = in.itemsVerified().trim();
        d.result = in.result();
        d.disqualified = disqualified;
        d.disqualificationGround = disqualified ? in.disqualificationGround().trim() : null;
        d.recordedBy = TenantContext.userOrNull();
        d.recordedAt = Instant.now();
    }

    // ================================================================ clarifications

    @Override
    @Transactional
    public Clarification createClarification(UUID fileId, ClarificationInput in) {
        ProcFile f = fileAccess.write(fileId);
        Evaluation ev = ensure(f);
        requireOpenEvaluation(ev);
        if (blank(in.bidderRef()) || in.issuedOn() == null || blank(in.subject())) {
            throw ApiException.badRequest("evaluation.clarification_invalid", "Bidder, date issued and subject are required");
        }
        Clarification c = new Clarification();
        c.fileId = f.id;
        c.bidderRef = in.bidderRef().trim();
        c.issuedOn = in.issuedOn();
        c.subject = in.subject().trim();
        c.recordedBy = TenantContext.userOrNull();
        applyResponse(c, in);
        clarifications.save(c);
        audit.created("clarification", c.id, c);
        return c;
    }

    @Override
    @Transactional
    public Clarification updateClarification(UUID id, ClarificationInput in) {
        Clarification c = clarifications.findById(id).orElseThrow(() -> ApiException.notFound("Clarification"));
        ProcFile f = fileAccess.write(c.fileId);
        requireOpenEvaluation(ensure(f));
        FileAccess.checkVersion(in.version(), c.version);
        var before = audit.snapshot(c);
        if (!blank(in.subject())) c.subject = in.subject().trim();
        if (in.issuedOn() != null) c.issuedOn = in.issuedOn();
        applyResponse(c, in);
        clarifications.save(c);
        audit.updated("clarification", c.id, before, c);
        return c;
    }

    /** Each response must be classified: did it clarify, or attempt to alter the bid? */
    private void applyResponse(Clarification c, ClarificationInput in) {
        String effect = in.effect() == null ? c.effect : in.effect();
        if (!Set.of("PENDING", "CLARIFIED", "ATTEMPTED_ALTERATION").contains(effect)) {
            throw ApiException.badRequest("evaluation.invalid_effect", "Unknown effect");
        }
        LocalDate received = in.responseReceivedOn() != null ? in.responseReceivedOn() : c.responseReceivedOn;
        String treatment = in.treatment() != null ? trim(in.treatment()) : c.treatment;
        if (received != null && "PENDING".equals(effect)) {
            throw ApiException.rule("evaluation.clarification_effect_required",
                    "Record whether the response clarified the bid or attempted to alter it");
        }
        if (!"PENDING".equals(effect) && (received == null || blank(treatment))) {
            throw ApiException.badRequest("evaluation.clarification_response_required",
                    "Record when the response was received and how the committee treated it");
        }
        if (received != null && received.isBefore(c.issuedOn)) {
            throw ApiException.badRequest("evaluation.response_before_request", "The response cannot predate the request");
        }
        c.responseReceivedOn = received;
        c.treatment = treatment;
        c.effect = effect;
        c.recordedAt = Instant.now();
    }

    private void requireOpenEvaluation(Evaluation ev) {
        boolean finished = stages.findByEvaluationIdOrderBySequenceAsc(ev.id).stream()
                .allMatch(s -> s.status == EvaluationStageStatus.COMPLETED);
        if (finished) {
            throw ApiException.rule("evaluation.closed", "The evaluation is complete");
        }
    }

    // ================================================================ helpers

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }

    private static String trim(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
