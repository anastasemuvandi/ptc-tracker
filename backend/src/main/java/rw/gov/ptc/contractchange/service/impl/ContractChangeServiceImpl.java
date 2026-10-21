package rw.gov.ptc.contractchange.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import rw.gov.ptc.audit.service.AuditService;
import rw.gov.ptc.common.ApiException;
import rw.gov.ptc.common.Time;
import rw.gov.ptc.config.component.ConfigKeys;
import rw.gov.ptc.config.dto.ConfigSnapshot;
import rw.gov.ptc.config.service.ConfigService;
import rw.gov.ptc.contract.entity.Contract;
import rw.gov.ptc.contract.repository.ContractRepository;
import rw.gov.ptc.contractchange.component.AmendmentAssessor;
import rw.gov.ptc.contractchange.dto.AmendmentInput;
import rw.gov.ptc.contractchange.dto.CostClaimInput;
import rw.gov.ptc.contractchange.dto.ExtensionInput;
import rw.gov.ptc.contractchange.entity.AmendmentRequest;
import rw.gov.ptc.contractchange.entity.ExtensionRequest;
import rw.gov.ptc.contractchange.enums.ChangeRequestStatus;
import rw.gov.ptc.contractchange.repository.AmendmentRequestRepository;
import rw.gov.ptc.contractchange.repository.ExtensionRequestRepository;
import rw.gov.ptc.contractchange.service.ContractChangeService;
import rw.gov.ptc.evaluation.component.FileAccess;
import rw.gov.ptc.evaluation.component.FileFactory;
import rw.gov.ptc.evaluation.component.IssuedDecisions;
import rw.gov.ptc.file.entity.ProcFile;
import rw.gov.ptc.file.repository.ProcFileRepository;
import rw.gov.ptc.tenancy.TenantContext;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
public class ContractChangeServiceImpl implements ContractChangeService {

    private static final Set<String> APPROVING_AMENDMENT = Set.of("APPROVE_AMENDMENT");
    private static final Set<String> REJECTING_AMENDMENT = Set.of("REJECT_AMENDMENT");
    private static final Set<String> GRANTING_EXTENSION = Set.of("GRANT_EXTENSION", "GRANT_PARTIAL_EXTENSION");
    private static final Set<String> REFUSING_EXTENSION = Set.of("REFUSE_EXTENSION", "APPLY_DELAY_PENALTIES");
    private static final Set<ChangeRequestStatus> DECIDED =
            EnumSet.of(ChangeRequestStatus.APPROVED, ChangeRequestStatus.REJECTED, ChangeRequestStatus.WITHDRAWN);

    private final FileAccess fileAccess;
    private final ProcFileRepository files;
    private final ContractRepository contracts;
    private final AmendmentRequestRepository amendments;
    private final ExtensionRequestRepository extensions;
    private final AmendmentAssessor assessor;
    private final ConfigService config;
    private final AuditService audit;
    private final IssuedDecisions decisions;
    private final FileFactory factory;
    private final ObjectMapper mapper;

    public ContractChangeServiceImpl(FileAccess fileAccess, ProcFileRepository files, ContractRepository contracts,
                                 AmendmentRequestRepository amendments, ExtensionRequestRepository extensions,
                                 AmendmentAssessor assessor, ConfigService config, AuditService audit,
                                 IssuedDecisions decisions, FileFactory factory, ObjectMapper mapper) {
        this.fileAccess = fileAccess;
        this.files = files;
        this.contracts = contracts;
        this.amendments = amendments;
        this.extensions = extensions;
        this.assessor = assessor;
        this.config = config;
        this.audit = audit;
        this.decisions = decisions;
        this.factory = factory;
        this.mapper = mapper;
    }

    private Contract contractOf(ProcFile f) {
        if (f.contractId == null) {
            throw ApiException.rule("contract.required", "This file is not linked to a contract");
        }
        return contracts.findById(f.contractId).orElseThrow(() -> ApiException.notFound("Contract"));
    }

    // ================================================================ amendment (C)

    @Override
    @Transactional
    public Map<String, Object> getAmendment(UUID fileId) {
        ProcFile f = fileAccess.read(fileId);
        FileAccess.requireType(f, "C");
        Contract c = contractOf(f);
        reconcileContract(c);
        return amendmentView(f, c);
    }

    private Map<String, Object> amendmentView(ProcFile f, Contract c) {
        Optional<AmendmentRequest> req = amendments.findByFileId(f.id);
        List<AmendmentRequest> all = amendments.findByContractIdOrderByCreatedAtAsc(c.id);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("fileId", f.id);
        out.put("fileNumber", f.fileNumber);
        out.put("contract", contractSummary(c));
        out.put("request", req.orElse(null));
        if (req.isPresent() && req.get().assessmentSnapshot != null) {
            JsonNode frozen = req.get().assessmentSnapshot;
            out.put("assessment", frozen.get("assessment"));
            out.put("frozen", true);
            out.put("frozenAt", frozen.path("frozenAt").asText());
        } else {
            out.put("assessment", assessor.assess(req.orElse(null), c, config.snapshot(), all));
            out.put("frozen", false);
        }
        out.put("otherAmendments", otherAmendments(c, req.map(r -> r.id).orElse(null), all));
        return out;
    }

    private List<Map<String, Object>> otherAmendments(Contract c, UUID exclude, List<AmendmentRequest> all) {
        Map<UUID, ProcFile> byId = new LinkedHashMap<>();
        files.findAllById(all.stream().map(a -> a.fileId).toList()).forEach(pf -> byId.put(pf.id, pf));
        List<Map<String, Object>> out = new ArrayList<>();
        for (AmendmentRequest a : all) {
            if (a.id.equals(exclude)) {
                continue;
            }
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("fileId", a.fileId);
            m.put("fileNumber", byId.containsKey(a.fileId) ? byId.get(a.fileId).fileNumber : null);
            m.put("status", a.status);
            m.put("requestedValue", AmendmentAssessor.money(a.requestedValue));
            m.put("approvedValue", a.approvedValue == null ? null : AmendmentAssessor.money(a.approvedValue));
            m.put("approvedAt", a.approvedAt);
            out.add(m);
        }
        return out;
    }

    @Override
    @Transactional
    public Map<String, Object> createAmendment(UUID fileId, AmendmentInput in) {
        ProcFile f = fileAccess.write(fileId);
        FileAccess.requireType(f, "C");
        Contract c = contractOf(f);
        reconcileContract(c);
        if (amendments.findByFileId(f.id).isPresent()) {
            throw ApiException.conflict("amendment.exists", "This file already has an amendment request");
        }
        AmendmentRequest a = new AmendmentRequest();
        a.fileId = f.id;
        a.contractId = c.id;
        a.currency = c.currency;
        a.createdBy = TenantContext.userOrNull();
        applyAmendment(a, c, in, true);
        amendments.save(a);
        audit.created("amendment_request", a.id, a);
        return amendmentView(f, c);
    }

    @Override
    @Transactional
    public Map<String, Object> updateAmendment(UUID fileId, AmendmentInput in) {
        ProcFile f = fileAccess.write(fileId);
        FileAccess.requireType(f, "C");
        Contract c = contractOf(f);
        reconcileContract(c);
        AmendmentRequest a = amendments.findByFileId(f.id).orElseThrow(() -> ApiException.notFound("Amendment request"));
        requireEditable(a.status, a.assessmentSnapshot);
        FileAccess.checkVersion(in.version(), a.version);
        var before = audit.snapshot(a);
        applyAmendment(a, c, in, false);
        amendments.save(a);
        audit.updated("amendment_request", a.id, before, a);
        return amendmentView(f, c);
    }

    private static void requireEditable(ChangeRequestStatus status, JsonNode snapshot) {
        if (snapshot != null || status != ChangeRequestStatus.OPEN) {
            throw ApiException.rule("amendment.frozen",
                    "The committee's recommendation has been issued; the request can no longer be changed");
        }
    }

    private void applyAmendment(AmendmentRequest a, Contract c, AmendmentInput in, boolean creating) {
        if (in.currency() != null && !in.currency().equalsIgnoreCase(c.currency)) {
            throw ApiException.rule("amendment.currency_mismatch",
                    "Amendments are expressed in the contract currency; no conversion is applied",
                    Map.of("contractCurrency", c.currency));
        }
        if (creating || in.requestedValue() != null) {
            if (in.requestedValue() == null || in.requestedValue().signum() < 0) {
                throw ApiException.badRequest("amendment.invalid_value", "Enter the value requested (zero or more)");
            }
            if (in.requestedValue().scale() > 2) {
                throw ApiException.badRequest("amendment.invalid_value", "At most two decimal places");
            }
            a.requestedValue = in.requestedValue();
        }
        if (creating || in.description() != null) {
            if (in.description() == null || in.description().isBlank()) {
                throw ApiException.badRequest("amendment.description_required", "Describe the amendment");
            }
            a.description = in.description().trim();
        }
        if (in.affectsSubstance() != null) {
            if (in.substanceJustification() == null || in.substanceJustification().isBlank()) {
                throw ApiException.badRequest("amendment.justification_required",
                        "Justify the answer on the substance and nature of the contract");
            }
            a.affectsSubstance = in.affectsSubstance();
            a.substanceJustification = in.substanceJustification().trim();
        }
        if (in.deliverablesChange() != null) a.deliverablesChange = in.deliverablesChange();
        if (in.perfSecurityExtend() != null) a.perfSecurityExtend = in.perfSecurityExtend();
        if (in.perfSecurityIncrease() != null) a.perfSecurityIncrease = in.perfSecurityIncrease();
        if (in.keyPersonnelChange() != null) a.keyPersonnelChange = in.keyPersonnelChange();
        if (in.replacementEqualOrBetter() != null) a.replacementEqualOrBetter = in.replacementEqualOrBetter();
    }

    @Override
    @Transactional
    public Map<String, Object> freezeAmendment(UUID fileId) {
        ProcFile f = fileAccess.write(fileId);
        FileAccess.requireType(f, "C");
        Contract c = contractOf(f);
        reconcileContract(c);
        AmendmentRequest a = amendments.findByFileId(f.id).orElseThrow(() -> ApiException.notFound("Amendment request"));
        if (a.assessmentSnapshot != null) {
            throw ApiException.conflict("amendment.already_frozen", "The assessment is already frozen");
        }
        if (a.affectsSubstance == null) {
            throw ApiException.rule("amendment.qualitative_unanswered",
                    "Answer the qualitative test before the recommendation");
        }
        freeze(a, c);
        return amendmentView(f, c);
    }

    private void freeze(AmendmentRequest a, Contract c) {
        var before = audit.snapshot(a);
        ConfigSnapshot cfg = config.snapshot();
        ObjectNode snap = mapper.createObjectNode();
        snap.put("frozenAt", Instant.now().toString());
        snap.set("assessment", mapper.valueToTree(
                assessor.assess(a, c, cfg, amendments.findByContractIdOrderByCreatedAtAsc(c.id))));
        snap.set("config", cfg.json());
        a.assessmentSnapshot = snap;
        if (a.status == ChangeRequestStatus.OPEN) {
            a.status = ChangeRequestStatus.RECOMMENDED;
        }
        amendments.save(a);
        audit.record("AMENDMENT_ASSESSMENT_FROZEN", "amendment_request", a.id, before, a);
    }

    // ================================================================ extension (D)

    @Override
    @Transactional
    public Map<String, Object> getExtension(UUID fileId) {
        ProcFile f = fileAccess.read(fileId);
        FileAccess.requireType(f, "D");
        Contract c = contractOf(f);
        reconcileContract(c);
        return extensionView(f, contracts.findById(c.id).orElse(c));
    }

    private Map<String, Object> extensionView(ProcFile f, Contract c) {
        Optional<ExtensionRequest> req = extensions.findByFileId(f.id);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("fileId", f.id);
        out.put("fileNumber", f.fileNumber);
        out.put("contract", contractSummary(c));
        out.put("request", req.orElse(null));
        out.put("grounds", config.snapshot().get(ConfigKeys.EXTENSION_GROUNDS));
        out.put("assessment", req.map(this::assessExtension).orElse(null));
        return out;
    }

    Map<String, Object> assessExtension(ExtensionRequest e) {
        ConfigSnapshot cfg = config.snapshot();
        JsonNode ground = ground(cfg, e.groundCode);
        boolean bidderFault = ground != null && ground.path("bidderFault").asBoolean(false);
        boolean receivedBefore = e.requestReceivedOn.isBefore(e.completionDateBefore);
        List<Map<String, Object>> warnings = new ArrayList<>();
        List<Map<String, Object>> blockers = new ArrayList<>();
        if (!receivedBefore) {
            warnings.add(AmendmentAssessor.warning("extension.received_after_completion",
                    "completionDate", e.completionDateBefore));
        }
        if (bidderFault) {
            warnings.add(AmendmentAssessor.warning("extension.penalties_may_apply"));
            if (!Boolean.TRUE.equals(e.penaltiesConsidered) || e.bidderFaultReasoning == null
                    || e.bidderFaultReasoning.isBlank()) {
                blockers.add(AmendmentAssessor.warning("extension.blocker.bidder_fault_reasoning"));
            }
        }
        if (e.hasCostClaim && e.linkedAmendmentFileId == null) {
            warnings.add(AmendmentAssessor.warning("extension.cost_claim_needs_type_c"));
        }
        Map<String, Object> fm = new LinkedHashMap<>();
        if (e.forceMajeureStart != null) {
            LocalDate end = e.forceMajeureEnd != null ? e.forceMajeureEnd : Time.today();
            long days = ChronoUnit.DAYS.between(e.forceMajeureStart, end);
            int limit = cfg.getInt(ConfigKeys.FORCE_MAJEURE_AUTO_CANCEL_DAYS);
            fm.put("start", e.forceMajeureStart);
            fm.put("end", e.forceMajeureEnd);
            fm.put("ongoing", e.forceMajeureEnd == null);
            fm.put("days", days);
            fm.put("autoCancelDays", limit);
            fm.put("daysUntilAutoCancel", limit - days);
            fm.put("exceeded", days > limit);
            if (days > limit) {
                warnings.add(AmendmentAssessor.warning("extension.force_majeure_auto_cancel",
                        "days", days, "autoCancelDays", limit));
            }
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("receivedBeforeCompletion", receivedBefore);
        out.put("ground", ground);
        out.put("bidderFault", bidderFault);
        out.put("penaltiesPrompt", bidderFault);
        out.put("reasoningRequired", bidderFault);
        out.put("costClaim", e.hasCostClaim);
        out.put("linkedAmendmentFileId", e.linkedAmendmentFileId);
        out.put("forceMajeure", fm.isEmpty() ? null : fm);
        out.put("extensionDays", ChronoUnit.DAYS.between(e.completionDateBefore, e.requestedCompletionDate));
        out.put("warnings", warnings);
        out.put("blockers", blockers);
        out.put("readyForRecommendation", blockers.isEmpty());
        return out;
    }

    private static JsonNode ground(ConfigSnapshot cfg, String code) {
        for (JsonNode g : cfg.get(ConfigKeys.EXTENSION_GROUNDS)) {
            if (g.path("code").asText().equals(code)) {
                return g;
            }
        }
        return null;
    }

    @Override
    @Transactional
    public Map<String, Object> createExtension(UUID fileId, ExtensionInput in) {
        ProcFile f = fileAccess.write(fileId);
        FileAccess.requireType(f, "D");
        Contract c = contractOf(f);
        if (extensions.findByFileId(f.id).isPresent()) {
            throw ApiException.conflict("extension.exists", "This file already has an extension request");
        }
        ExtensionRequest e = new ExtensionRequest();
        e.fileId = f.id;
        e.contractId = c.id;
        e.completionDateBefore = c.currentCompletionDate;
        e.createdBy = TenantContext.userOrNull();
        applyExtension(e, in, true);
        extensions.save(e);
        audit.created("extension_request", e.id, e);
        return extensionView(f, c);
    }

    @Override
    @Transactional
    public Map<String, Object> updateExtension(UUID fileId, ExtensionInput in) {
        ProcFile f = fileAccess.write(fileId);
        FileAccess.requireType(f, "D");
        Contract c = contractOf(f);
        ExtensionRequest e = extensions.findByFileId(f.id).orElseThrow(() -> ApiException.notFound("Extension request"));
        if (e.status != ChangeRequestStatus.OPEN) {
            throw ApiException.rule("extension.frozen",
                    "The committee's recommendation has been issued; the request can no longer be changed");
        }
        FileAccess.checkVersion(in.version(), e.version);
        var before = audit.snapshot(e);
        applyExtension(e, in, false);
        extensions.save(e);
        audit.updated("extension_request", e.id, before, e);
        return extensionView(f, c);
    }

    private void applyExtension(ExtensionRequest e, ExtensionInput in, boolean creating) {
        if (creating || in.requestedCompletionDate() != null) {
            if (in.requestedCompletionDate() == null || !in.requestedCompletionDate().isAfter(e.completionDateBefore)) {
                throw ApiException.rule("extension.date_not_later",
                        "The requested date must be after the current completion date",
                        Map.of("completionDate", e.completionDateBefore));
            }
            e.requestedCompletionDate = in.requestedCompletionDate();
        }
        if (creating || in.groundCode() != null) {
            if (ground(config.snapshot(), in.groundCode()) == null) {
                throw ApiException.badRequest("extension.unknown_ground", "Select a ground from the list");
            }
            e.groundCode = in.groundCode();
        }
        if (creating || in.groundDetails() != null) {
            if (in.groundDetails() == null || in.groundDetails().isBlank()) {
                throw ApiException.badRequest("extension.ground_details_required", "Describe the ground");
            }
            e.groundDetails = in.groundDetails().trim();
        }
        if (creating || in.requestReceivedOn() != null) {
            if (in.requestReceivedOn() == null) {
                throw ApiException.badRequest("extension.received_date_required", "Enter the date the request was received");
            }
            e.requestReceivedOn = in.requestReceivedOn();
        }
        if (in.penaltiesConsidered() != null) e.penaltiesConsidered = in.penaltiesConsidered();
        if (in.bidderFaultReasoning() != null) {
            e.bidderFaultReasoning = in.bidderFaultReasoning().isBlank() ? null : in.bidderFaultReasoning().trim();
        }
        if (in.forceMajeureStart() != null) e.forceMajeureStart = in.forceMajeureStart();
        if (in.forceMajeureEnd() != null) e.forceMajeureEnd = in.forceMajeureEnd();
        if (e.forceMajeureEnd != null && (e.forceMajeureStart == null || e.forceMajeureEnd.isBefore(e.forceMajeureStart))) {
            throw ApiException.badRequest("extension.force_majeure_dates", "Force majeure must end after it starts");
        }
        if (!"FORCE_MAJEURE".equals(e.groundCode) && (e.forceMajeureStart != null)) {
            throw ApiException.badRequest("extension.force_majeure_dates", "Force majeure dates apply to the force majeure ground only");
        }
    }

    @Override
    @Transactional
    public Map<String, Object> raiseCostClaim(UUID fileId, CostClaimInput in) {
        ProcFile f = fileAccess.write(fileId);
        FileAccess.requireType(f, "D");
        Contract c = contractOf(f);
        ExtensionRequest e = extensions.findByFileId(f.id).orElseThrow(() -> ApiException.notFound("Extension request"));
        if (e.linkedAmendmentFileId != null) {
            throw ApiException.conflict("extension.cost_claim_exists", "A cost claim file is already linked");
        }
        ProcFile claim = factory.derive(f, "C", "Cost claim — " + f.title, "Cost claim raised from extension " + f.fileNumber);
        AmendmentRequest a = new AmendmentRequest();
        a.fileId = claim.id;
        a.contractId = c.id;
        a.currency = c.currency;
        a.linkedExtensionFileId = f.id;
        a.createdBy = TenantContext.userOrNull();
        applyAmendment(a, c, new AmendmentInput(in.requestedValue(), null,
                in.description() == null || in.description().isBlank()
                        ? "Cost claim arising from time extension " + f.fileNumber : in.description(),
                null, null, null, null, null, null, null, null), true);
        amendments.save(a);
        audit.created("amendment_request", a.id, a);
        var before = audit.snapshot(e);
        e.hasCostClaim = true;
        e.linkedAmendmentFileId = claim.id;
        extensions.save(e);
        audit.updated("extension_request", e.id, before, e);
        return Map.of("fileId", claim.id, "fileNumber", claim.fileNumber);
    }

    // ================================================================ reconciliation

    @Override
    @Transactional
    public void reconcileContract(Contract c) {
        for (AmendmentRequest a : amendments.findByContractIdOrderByCreatedAtAsc(c.id)) {
            reconcileAmendment(a, c);
        }
        for (ExtensionRequest e : extensions.findByContractIdOrderByCreatedAtAsc(c.id)) {
            reconcileExtension(e, c);
        }
    }

    private void reconcileAmendment(AmendmentRequest a, Contract c) {
        if (DECIDED.contains(a.status)) {
            return;
        }
        Optional<Map<String, Object>> issued = decisions.latestIssued(a.fileId);
        if (issued.isEmpty()) {
            return;
        }
        if (a.assessmentSnapshot == null) {
            freeze(a, c);
        }
        Map<String, Object> d = issued.get();
        decisions.latestCbmResponse((UUID) d.get("id"))
                .filter(r -> "APPROVED".equals(r.get("response")))
                .ifPresent(r -> {
                    String code = (String) d.get("recommendationCode");
                    ChangeRequestStatus to = APPROVING_AMENDMENT.contains(code) ? ChangeRequestStatus.APPROVED
                            : REJECTING_AMENDMENT.contains(code) ? ChangeRequestStatus.REJECTED : null;
                    if (to == null) {
                        return; // escalated: approval rests with the supervising authority
                    }
                    var before = audit.snapshot(a);
                    a.status = to;
                    if (to == ChangeRequestStatus.APPROVED) {
                        if (a.approvedValue == null) {
                            a.approvedValue = a.requestedValue;
                        }
                        a.approvedAt = r.get("respondedAt") instanceof Instant i ? i : Instant.now();
                    }
                    amendments.save(a);
                    audit.record("AMENDMENT_" + to, "amendment_request", a.id, before, a);
                });
    }

    private void reconcileExtension(ExtensionRequest e, Contract c) {
        if (DECIDED.contains(e.status)) {
            return;
        }
        Optional<Map<String, Object>> issued = decisions.latestIssued(e.fileId);
        if (issued.isEmpty()) {
            return;
        }
        Map<String, Object> d = issued.get();
        if (e.status == ChangeRequestStatus.OPEN) {
            var before = audit.snapshot(e);
            e.status = ChangeRequestStatus.RECOMMENDED;
            extensions.save(e);
            audit.updated("extension_request", e.id, before, e);
        }
        decisions.latestCbmResponse((UUID) d.get("id"))
                .filter(r -> "APPROVED".equals(r.get("response")))
                .ifPresent(r -> {
                    String code = (String) d.get("recommendationCode");
                    ChangeRequestStatus to = GRANTING_EXTENSION.contains(code) ? ChangeRequestStatus.APPROVED
                            : REFUSING_EXTENSION.contains(code) ? ChangeRequestStatus.REJECTED : null;
                    if (to == null) {
                        return;
                    }
                    var before = audit.snapshot(e);
                    e.status = to;
                    e.approvedAt = r.get("respondedAt") instanceof Instant i ? i : Instant.now();
                    if (to == ChangeRequestStatus.APPROVED) {
                        e.approvedCompletionDate = approvedDate(d.get("structuredOutcome"), e.requestedCompletionDate);
                    }
                    extensions.save(e);
                    audit.record("EXTENSION_" + to, "extension_request", e.id, before, e);
                    if (to == ChangeRequestStatus.APPROVED && !e.approvedCompletionDate.equals(c.currentCompletionDate)) {
                        var contractBefore = audit.snapshot(c);
                        c.currentCompletionDate = e.approvedCompletionDate;
                        contracts.save(c);
                        audit.record("CONTRACT_COMPLETION_EXTENDED", "contract", c.id, contractBefore, c);
                    }
                });
    }

    private static LocalDate approvedDate(Object structuredOutcome, LocalDate fallback) {
        if (structuredOutcome instanceof JsonNode n && n.hasNonNull("approvedCompletionDate")) {
            try {
                return LocalDate.parse(n.get("approvedCompletionDate").asText());
            } catch (java.time.format.DateTimeParseException ignored) {
                // fall back to the requested date
            }
        }
        return fallback;
    }

    static Map<String, Object> contractSummary(Contract c) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", c.id);
        m.put("contractRef", c.contractRef);
        m.put("title", c.title);
        m.put("procurementType", c.procurementType);
        m.put("currency", c.currency);
        m.put("originalValue", AmendmentAssessor.money(c.originalValue));
        m.put("originalCompletionDate", c.originalCompletionDate);
        m.put("currentCompletionDate", c.currentCompletionDate);
        return m;
    }
}
