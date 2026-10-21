package rw.gov.ptc.evaluation.service;

import com.fasterxml.jackson.databind.JsonNode;
import rw.gov.ptc.evaluation.dto.AbnormalInput;
import rw.gov.ptc.evaluation.dto.BidderInput;
import rw.gov.ptc.evaluation.dto.ClarificationInput;
import rw.gov.ptc.evaluation.dto.CureInput;
import rw.gov.ptc.evaluation.dto.DiligenceInput;
import rw.gov.ptc.evaluation.entity.AbnormalPriceCheck;
import rw.gov.ptc.evaluation.entity.Clarification;
import rw.gov.ptc.evaluation.entity.DueDiligence;
import rw.gov.ptc.file.entity.ProcFile;

import java.util.Map;
import java.util.UUID;

/**
 * Type A — evaluation report review. Tracks the committee's progress through
 * the configured stages (A1–A6 by default) and the clocks that run during
 * the process. Scores, prices and bidder documents stay in Umucyo: only
 * pass/fail, grounds, ranks by bidder reference and the committee's
 * reasoning are recorded here.
 */
public interface EvaluationService {

    Map<String, Object> progress(UUID fileId);

    /**
     * The bid validity clock: the extension request must reach the regulator
     * regulatorLeadDays before expiry. Live clocks use the rules in force now.
     */
    Map<String, Object> bidValidity(ProcFile f);

    Map<String, Object> lockCriteria(UUID fileId, boolean confirmed, String findings);

    /** Defects in the published criteria are findings to report, not criteria to adjust. */
    Map<String, Object> recordFindings(UUID fileId, String findings);

    Map<String, Object> startStage(UUID fileId, String code);

    Map<String, Object> updateStage(UUID fileId, String code, JsonNode data, String outcome, Long version);

    Map<String, Object> completeStage(UUID fileId, String code, String outcome);

    Map<String, Object> recordBidder(UUID fileId, String code, BidderInput in);

    AbnormalPriceCheck createAbnormal(UUID fileId, AbnormalInput in);

    AbnormalPriceCheck updateAbnormal(UUID id, AbnormalInput in);

    Map<String, Object> raiseRejectionOfAll(UUID fileId, String ground, String reason);

    Map<String, Object> createCure(UUID fileId, CureInput in);

    Map<String, Object> updateCure(UUID id, CureInput in);

    DueDiligence createDiligence(UUID fileId, DiligenceInput in);

    DueDiligence updateDiligence(UUID id, DiligenceInput in);

    Clarification createClarification(UUID fileId, ClarificationInput in);

    Clarification updateClarification(UUID id, ClarificationInput in);
}
