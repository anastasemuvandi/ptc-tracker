package rw.gov.ptc.contractchange;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import rw.gov.ptc.auth.api.AuthPrincipal;
import rw.gov.ptc.common.ApiException;
import rw.gov.ptc.common.Time;
import rw.gov.ptc.config.ConfigKeys;
import rw.gov.ptc.config.ConfigService;
import rw.gov.ptc.config.ConfigSnapshot;
import rw.gov.ptc.contract.Contract;
import rw.gov.ptc.contract.ContractRepository;
import rw.gov.ptc.file.ProcFile;
import rw.gov.ptc.file.ProcFileRepository;
import rw.gov.ptc.security.Access;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Every amendment and extension against one contract over its life, with
 * the running cumulative amendment value (approved amendments, in the
 * contract currency), the current completion date and the performance
 * security status.
 */
@Service
public class ContractTimelineService {

    private final ContractRepository contracts;
    private final AmendmentRequestRepository amendments;
    private final ExtensionRequestRepository extensions;
    private final ProcFileRepository files;
    private final ContractChangeService changes;
    private final ConfigService config;
    private final Access access;

    public ContractTimelineService(ContractRepository contracts, AmendmentRequestRepository amendments,
                                   ExtensionRequestRepository extensions, ProcFileRepository files,
                                   ContractChangeService changes, ConfigService config, Access access) {
        this.contracts = contracts;
        this.amendments = amendments;
        this.extensions = extensions;
        this.files = files;
        this.changes = changes;
        this.config = config;
        this.access = access;
    }

    private record Event(Instant sortKey, Map<String, Object> body) {
    }

    @Transactional
    public Map<String, Object> timeline(UUID contractId) {
        AuthPrincipal p = access.principal();
        if (p.committees().isEmpty() && !access.canReadAllCommittees()) {
            throw ApiException.forbidden("committee.no_access", "You do not have access to contracts");
        }
        Contract c = contracts.findById(contractId).orElseThrow(() -> ApiException.notFound("Contract"));
        changes.reconcileContract(c);
        c = contracts.findById(contractId).orElseThrow();
        ConfigSnapshot cfg = config.snapshot();

        List<AmendmentRequest> amends = amendments.findByContractIdOrderByCreatedAtAsc(c.id);
        List<ExtensionRequest> exts = extensions.findByContractIdOrderByCreatedAtAsc(c.id);
        Map<UUID, ProcFile> fileById = new LinkedHashMap<>();
        List<UUID> fileIds = new ArrayList<>();
        amends.forEach(a -> fileIds.add(a.fileId));
        exts.forEach(e -> fileIds.add(e.fileId));
        files.findAllById(fileIds).forEach(f -> fileById.put(f.id, f));

        List<Event> events = new ArrayList<>();
        for (AmendmentRequest a : amends) {
            Instant when = a.approvedAt != null ? a.approvedAt : a.createdAt;
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("date", Time.localDate(when));
            m.put("kind", "AMENDMENT");
            m.put("fileId", a.fileId);
            m.put("fileNumber", fileById.containsKey(a.fileId) ? fileById.get(a.fileId).fileNumber : null);
            m.put("status", a.status);
            m.put("description", a.description);
            m.put("value", AmendmentAssessor.money("APPROVED".equals(a.status) ? a.approvedValue : a.requestedValue));
            m.put("linkedExtensionFileId", a.linkedExtensionFileId);
            events.add(new Event(when, m));
        }
        for (ExtensionRequest e : exts) {
            Instant when = e.approvedAt != null ? e.approvedAt : e.createdAt;
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("date", Time.localDate(when));
            m.put("kind", "EXTENSION");
            m.put("fileId", e.fileId);
            m.put("fileNumber", fileById.containsKey(e.fileId) ? fileById.get(e.fileId).fileNumber : null);
            m.put("status", e.status);
            m.put("groundCode", e.groundCode);
            m.put("previousCompletionDate", e.completionDateBefore);
            m.put("newCompletionDate", "APPROVED".equals(e.status) ? e.approvedCompletionDate : e.requestedCompletionDate);
            m.put("linkedAmendmentFileId", e.linkedAmendmentFileId);
            events.add(new Event(when, m));
        }
        events.sort(Comparator.comparing(Event::sortKey));

        BigDecimal ceilingPct = cfg.getDecimal(ConfigKeys.AMENDMENT_CEILING_PCT);
        BigDecimal warningPct = cfg.getDecimal(ConfigKeys.AMENDMENT_WARNING_PCT);
        BigDecimal running = BigDecimal.ZERO;
        List<Map<String, Object>> out = new ArrayList<>();
        for (Event ev : events) {
            Map<String, Object> m = ev.body();
            if ("AMENDMENT".equals(m.get("kind"))) {
                BigDecimal value = new BigDecimal((String) m.get("value"));
                if ("APPROVED".equals(m.get("status"))) {
                    running = running.add(value);
                    m.put("runningCumulative", AmendmentAssessor.money(running));
                    m.put("runningPct", AmendmentAssessor.pct(running, c.originalValue));
                } else if (AmendmentAssessor.PENDING.contains((String) m.get("status"))) {
                    // What the cumulative figure would become if this were approved.
                    BigDecimal projected = running.add(value);
                    m.put("projectedCumulative", AmendmentAssessor.money(projected));
                    m.put("projectedPct", AmendmentAssessor.pct(projected, c.originalValue));
                }
            }
            out.add(m);
        }
        BigDecimal cumulativePct = AmendmentAssessor.pct(running, c.originalValue);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("contract", c);
        result.put("events", out);
        result.put("currency", c.currency);
        result.put("cumulativeApproved", AmendmentAssessor.money(running));
        result.put("cumulativePct", cumulativePct);
        result.put("ceilingPct", ceilingPct);
        result.put("warningPct", warningPct);
        result.put("level", AmendmentAssessor.level(cumulativePct, ceilingPct, warningPct));
        result.put("headroom", AmendmentAssessor.money(c.originalValue.multiply(ceilingPct)
                .divide(BigDecimal.valueOf(100), 2, java.math.RoundingMode.HALF_UP).subtract(running)));
        result.put("originalCompletionDate", c.originalCompletionDate);
        result.put("currentCompletionDate", c.currentCompletionDate);
        result.put("perfSecurity", perfSecurity(c, cfg.getInt(ConfigKeys.CONTRACT_COMPLETION_ALERT_DAYS)));
        return result;
    }

    static Map<String, Object> perfSecurity(Contract c, int alertDays) {
        Map<String, Object> m = new LinkedHashMap<>();
        LocalDate expiry = c.perfSecurityExpiry;
        m.put("expiry", expiry);
        m.put("ref", c.perfSecurityRef);
        m.put("amount", c.perfSecurityAmount == null ? null : AmendmentAssessor.money(c.perfSecurityAmount));
        m.put("currency", c.perfSecurityCurrency);
        LocalDate today = Time.today();
        String state;
        if (expiry == null) {
            state = "NONE";
        } else if (expiry.isBefore(today)) {
            state = "EXPIRED";
        } else if (!expiry.isAfter(today.plusDays(alertDays))) {
            state = "EXPIRING";
        } else {
            state = "VALID";
        }
        m.put("state", state);
        // A security that lapses before the (possibly extended) completion date needs extending.
        m.put("coversCompletion", expiry != null && !expiry.isBefore(c.currentCompletionDate));
        return m;
    }
}
