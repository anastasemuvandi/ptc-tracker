package rw.gov.ptc.contractchange;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;
import rw.gov.ptc.common.Time;
import rw.gov.ptc.config.ConfigKeys;
import rw.gov.ptc.config.ConfigSnapshot;
import rw.gov.ptc.contract.Contract;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The type C control. The amendment ceiling applies to the contract
 * CUMULATIVELY: every approved amendment on the contract is summed with the
 * one requested, in the contract's own currency, so that successive
 * amendments each under the ceiling but together above it are visible.
 *
 * <p>Two independent tests: the qualitative test (does it change the
 * substance and nature of the contract?) is an absolute bar; the
 * quantitative test (percentage and absolute ceilings) only decides the
 * approval route. Passing the quantitative test never cures a qualitative
 * failure.
 */
@Component
public class AmendmentAssessor {

    static final Set<String> PENDING = Set.of("OPEN", "RECOMMENDED");

    public Map<String, Object> assess(AmendmentRequest request, Contract contract, ConfigSnapshot cfg,
                                      List<AmendmentRequest> contractAmendments) {
        BigDecimal original = contract.originalValue;
        BigDecimal requested = request == null || request.requestedValue == null ? BigDecimal.ZERO : request.requestedValue;
        BigDecimal priorApproved = BigDecimal.ZERO;
        BigDecimal pendingOthers = BigDecimal.ZERO;
        for (AmendmentRequest other : contractAmendments) {
            if (request != null && other.id.equals(request.id)) {
                continue;
            }
            if ("APPROVED".equals(other.status)) {
                priorApproved = priorApproved.add(other.approvedValue);
            } else if (PENDING.contains(other.status)) {
                pendingOthers = pendingOthers.add(other.requestedValue);
            }
        }
        BigDecimal resulting = priorApproved.add(requested);
        BigDecimal ceilingPct = cfg.getDecimal(ConfigKeys.AMENDMENT_CEILING_PCT);
        BigDecimal warningPct = cfg.getDecimal(ConfigKeys.AMENDMENT_WARNING_PCT);
        BigDecimal priorPct = pct(priorApproved, original);
        BigDecimal resultingPct = pct(resulting, original);
        BigDecimal includingPendingPct = pct(resulting.add(pendingOthers), original);

        BigDecimal absoluteCeiling = null;
        JsonNode ceilings = cfg.get(ConfigKeys.AMENDMENT_ABSOLUTE_CEILINGS);
        JsonNode forCurrency = ceilings.get(contract.currency);
        if (forCurrency != null && !forCurrency.isNull()) {
            absoluteCeiling = new BigDecimal(forCurrency.asText());
        }

        boolean withinPct = resultingPct.compareTo(ceilingPct) <= 0;
        Boolean withinAbsolute = absoluteCeiling == null ? null : resulting.compareTo(absoluteCeiling) <= 0;
        boolean quantitativePasses = withinPct && !Boolean.FALSE.equals(withinAbsolute);

        Boolean affects = request == null ? null : request.affectsSubstance;
        Boolean qualitativePasses = affects == null ? null : !affects;
        String route = quantitativePasses ? "ENTITY" : "ESCALATED";

        List<Map<String, Object>> warnings = new ArrayList<>();
        String level = level(resultingPct, ceilingPct, warningPct);
        if ("EXCEEDED".equals(level)) {
            warnings.add(warning("amendment.cumulative_exceeded", "resultingPct", resultingPct, "ceilingPct", ceilingPct));
        } else if ("APPROACHING".equals(level)) {
            warnings.add(warning("amendment.approaching_ceiling", "resultingPct", resultingPct, "ceilingPct", ceilingPct));
        }
        if (!"EXCEEDED".equals(level) && includingPendingPct.compareTo(ceilingPct) > 0) {
            warnings.add(warning("amendment.pending_amendments_exceed_ceiling", "includingPendingPct", includingPendingPct));
        }
        if (Boolean.FALSE.equals(withinAbsolute)) {
            warnings.add(warning("amendment.absolute_ceiling_exceeded", "absoluteCeiling", money(absoluteCeiling)));
        }
        if (absoluteCeiling == null) {
            warnings.add(warning("amendment.no_absolute_ceiling_for_currency", "currency", contract.currency));
        }
        if (Boolean.TRUE.equals(affects)) {
            warnings.add(warning("amendment.qualitative_bar"));
        }

        LocalDate today = Time.today();
        int leadDays = cfg.getInt(ConfigKeys.AMENDMENT_ESCALATION_LEAD_DAYS);
        LocalDate submitBy = contract.currentCompletionDate.minusDays(leadDays);
        long daysRemaining = ChronoUnit.DAYS.between(today, submitBy);
        Map<String, Object> escalation = new LinkedHashMap<>();
        escalation.put("applies", "ESCALATED".equals(route));
        escalation.put("leadDays", leadDays);
        escalation.put("completionDate", contract.currentCompletionDate);
        escalation.put("submitBy", submitBy);
        escalation.put("daysRemaining", daysRemaining);
        escalation.put("sufficient", daysRemaining >= 0);
        if ("ESCALATED".equals(route) && daysRemaining < 0) {
            warnings.add(warning("amendment.escalation_time_insufficient", "submitBy", submitBy));
        }
        if (contract.currentCompletionDate.isBefore(today)) {
            warnings.add(warning("amendment.contract_completion_passed", "completionDate", contract.currentCompletionDate));
        }

        Map<String, Object> prompts = new LinkedHashMap<>();
        boolean valueIncrease = requested.signum() > 0;
        prompts.put("perfSecurityReviewRequired", valueIncrease);
        prompts.put("perfSecurityExtend", request == null ? null : request.perfSecurityExtend);
        prompts.put("perfSecurityIncrease", request == null ? null : request.perfSecurityIncrease);
        prompts.put("deliverablesChange", request == null ? null : request.deliverablesChange);
        boolean consultancy = "CONSULTANCY".equals(contract.procurementType);
        prompts.put("keyPersonnelApplies", consultancy);
        prompts.put("keyPersonnelChange", request == null ? null : request.keyPersonnelChange);
        prompts.put("replacementEqualOrBetter", request == null ? null : request.replacementEqualOrBetter);
        if (request != null && valueIncrease && request.perfSecurityIncrease == null) {
            warnings.add(warning("amendment.perf_security_unanswered"));
        }
        if (request != null && consultancy && Boolean.TRUE.equals(request.keyPersonnelChange)) {
            if (request.replacementEqualOrBetter == null) {
                warnings.add(warning("amendment.key_personnel_unanswered"));
            } else if (!request.replacementEqualOrBetter) {
                warnings.add(warning("amendment.key_personnel_not_equal_or_better"));
            }
        }

        Map<String, Object> qualitative = new LinkedHashMap<>();
        qualitative.put("answered", affects != null);
        qualitative.put("affectsSubstance", affects);
        qualitative.put("justification", request == null ? null : request.substanceJustification);
        qualitative.put("passes", qualitativePasses);
        Map<String, Object> quantitative = new LinkedHashMap<>();
        quantitative.put("withinPct", withinPct);
        quantitative.put("withinAbsolute", withinAbsolute);
        quantitative.put("passes", quantitativePasses);
        quantitative.put("note", "amendment.quantitative_route_only");

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("currency", contract.currency);
        out.put("originalValue", money(original));
        out.put("priorApprovedCumulative", money(priorApproved));
        out.put("priorApprovedPct", priorPct);
        out.put("pendingOtherAmendments", money(pendingOthers));
        out.put("requestedValue", money(requested));
        out.put("resultingCumulative", money(resulting));
        out.put("resultingPct", resultingPct);
        out.put("includingPendingPct", includingPendingPct);
        out.put("ceilingPct", ceilingPct);
        out.put("warningPct", warningPct);
        out.put("absoluteCeiling", absoluteCeiling == null ? null : money(absoluteCeiling));
        out.put("level", level);
        out.put("qualitative", qualitative);
        out.put("quantitative", quantitative);
        out.put("route", route);
        out.put("barred", Boolean.TRUE.equals(affects));
        out.put("admissible", Boolean.TRUE.equals(qualitativePasses));
        out.put("escalation", escalation);
        out.put("prompts", prompts);
        out.put("warnings", warnings);
        out.put("preview", request == null);
        return out;
    }

    static String level(BigDecimal pct, BigDecimal ceiling, BigDecimal warning) {
        if (pct.compareTo(ceiling) > 0) {
            return "EXCEEDED";
        }
        return pct.compareTo(warning) >= 0 ? "APPROACHING" : "OK";
    }

    static BigDecimal pct(BigDecimal part, BigDecimal whole) {
        return part.multiply(BigDecimal.valueOf(100)).divide(whole, 4, RoundingMode.HALF_UP)
                .setScale(2, RoundingMode.HALF_UP);
    }

    static String money(BigDecimal v) {
        return v.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    static Map<String, Object> warning(String code, Object... kv) {
        Map<String, Object> params = new LinkedHashMap<>();
        for (int i = 0; i + 1 < kv.length; i += 2) {
            params.put((String) kv[i], kv[i + 1]);
        }
        return Map.of("code", code, "params", params);
    }
}
