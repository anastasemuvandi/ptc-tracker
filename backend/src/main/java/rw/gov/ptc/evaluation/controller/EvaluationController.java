package rw.gov.ptc.evaluation.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import rw.gov.ptc.evaluation.dto.AbnormalInput;
import rw.gov.ptc.evaluation.dto.BidderInput;
import rw.gov.ptc.evaluation.dto.ChecklistAnswer;
import rw.gov.ptc.evaluation.dto.ClarificationInput;
import rw.gov.ptc.evaluation.dto.CriteriaLockRequest;
import rw.gov.ptc.evaluation.dto.CureInput;
import rw.gov.ptc.evaluation.dto.DiligenceInput;
import rw.gov.ptc.evaluation.dto.FindingsRequest;
import rw.gov.ptc.evaluation.dto.OutcomeRequest;
import rw.gov.ptc.evaluation.dto.RejectionRequest;
import rw.gov.ptc.evaluation.dto.StageUpdate;
import rw.gov.ptc.evaluation.entity.AbnormalPriceCheck;
import rw.gov.ptc.evaluation.entity.Clarification;
import rw.gov.ptc.evaluation.entity.DueDiligence;
import rw.gov.ptc.evaluation.service.ChecklistService;
import rw.gov.ptc.evaluation.service.EvaluationService;

import java.util.Map;
import java.util.UUID;

/** Type A stage progress view (the committee's primary screen for evaluations) and checklists. */
@RestController
@RequestMapping("/api")
public class EvaluationController {

    private final EvaluationService evaluation;
    private final ChecklistService checklist;

    public EvaluationController(EvaluationService evaluation, ChecklistService checklist) {
        this.evaluation = evaluation;
        this.checklist = checklist;
    }

    @GetMapping("/files/{id}/evaluation")
    public Map<String, Object> progress(@PathVariable UUID id) {
        return evaluation.progress(id);
    }

    @PostMapping("/files/{id}/evaluation/criteria-lock")
    public Map<String, Object> lock(@PathVariable UUID id, @RequestBody CriteriaLockRequest req) {
        return evaluation.lockCriteria(id, req.confirmed(), req.defectFindings());
    }

    @PutMapping("/files/{id}/evaluation/findings")
    public Map<String, Object> findings(@PathVariable UUID id, @RequestBody FindingsRequest req) {
        return evaluation.recordFindings(id, req.defectFindings());
    }

    @PostMapping("/files/{id}/evaluation/stages/{code}/start")
    public Map<String, Object> start(@PathVariable UUID id, @PathVariable String code) {
        return evaluation.startStage(id, code);
    }

    @PutMapping("/files/{id}/evaluation/stages/{code}")
    public Map<String, Object> update(@PathVariable UUID id, @PathVariable String code, @RequestBody StageUpdate req) {
        return evaluation.updateStage(id, code, req.data(), req.outcome(), req.version());
    }

    @PostMapping("/files/{id}/evaluation/stages/{code}/complete")
    public Map<String, Object> complete(@PathVariable UUID id, @PathVariable String code,
                                        @RequestBody(required = false) OutcomeRequest req) {
        return evaluation.completeStage(id, code, req == null ? null : req.outcome());
    }

    @PutMapping("/files/{id}/evaluation/stages/{code}/bidders")
    public Map<String, Object> bidder(@PathVariable UUID id, @PathVariable String code,
                                      @RequestBody BidderInput req) {
        return evaluation.recordBidder(id, code, req);
    }

    @PostMapping("/files/{id}/abnormal-prices")
    public AbnormalPriceCheck createAbnormal(@PathVariable UUID id, @RequestBody AbnormalInput req) {
        return evaluation.createAbnormal(id, req);
    }

    @PutMapping("/abnormal-prices/{id}")
    public AbnormalPriceCheck updateAbnormal(@PathVariable UUID id, @RequestBody AbnormalInput req) {
        return evaluation.updateAbnormal(id, req);
    }

    @PostMapping("/files/{id}/cure-requests")
    public Map<String, Object> createCure(@PathVariable UUID id, @RequestBody CureInput req) {
        return evaluation.createCure(id, req);
    }

    @PutMapping("/cure-requests/{id}")
    public Map<String, Object> updateCure(@PathVariable UUID id, @RequestBody CureInput req) {
        return evaluation.updateCure(id, req);
    }

    @PostMapping("/files/{id}/due-diligence")
    public DueDiligence createDiligence(@PathVariable UUID id, @RequestBody DiligenceInput req) {
        return evaluation.createDiligence(id, req);
    }

    @PutMapping("/due-diligence/{id}")
    public DueDiligence updateDiligence(@PathVariable UUID id, @RequestBody DiligenceInput req) {
        return evaluation.updateDiligence(id, req);
    }

    @PostMapping("/files/{id}/clarifications")
    public Clarification createClarification(@PathVariable UUID id,
                                             @RequestBody ClarificationInput req) {
        return evaluation.createClarification(id, req);
    }

    @PutMapping("/clarifications/{id}")
    public Clarification updateClarification(@PathVariable UUID id,
                                             @RequestBody ClarificationInput req) {
        return evaluation.updateClarification(id, req);
    }

    @PostMapping("/files/{id}/evaluation/raise-rejection-of-all")
    public Map<String, Object> raiseRejection(@PathVariable UUID id, @RequestBody RejectionRequest req) {
        return evaluation.raiseRejectionOfAll(id, req.ground(), req.reason());
    }

    @GetMapping("/files/{id}/checklist")
    public Map<String, Object> checklist(@PathVariable UUID id) {
        return checklist.get(id);
    }

    @PutMapping("/files/{id}/checklist/{itemCode}")
    public Map<String, Object> answer(@PathVariable UUID id, @PathVariable String itemCode,
                                      @RequestBody ChecklistAnswer req) {
        return checklist.answer(id, itemCode, req.answer(), req.note(), req.version());
    }
}
