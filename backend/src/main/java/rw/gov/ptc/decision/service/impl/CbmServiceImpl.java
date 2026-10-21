package rw.gov.ptc.decision.service.impl;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import rw.gov.ptc.audit.service.AuditService;
import rw.gov.ptc.common.ApiException;
import rw.gov.ptc.common.Rows;
import rw.gov.ptc.decision.dto.ResponseRequest;
import rw.gov.ptc.decision.entity.CbmResponse;
import rw.gov.ptc.decision.entity.Decision;
import rw.gov.ptc.decision.enums.DecisionStatus;
import rw.gov.ptc.decision.repository.CbmResponseRepository;
import rw.gov.ptc.decision.repository.DecisionRepository;
import rw.gov.ptc.decision.service.CbmService;
import rw.gov.ptc.file.component.FileStages;
import rw.gov.ptc.file.repository.ProcFileRepository;
import rw.gov.ptc.security.Access;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class CbmServiceImpl implements CbmService {

    private static final Set<String> RESPONSES = Set.of("APPROVED", "RETURNED_FOR_CORRECTION", "OTHER");

    private final DecisionRepository decisions;
    private final CbmResponseRepository responses;
    private final ProcFileRepository files;
    private final FileStages stages;
    private final AuditService audit;
    private final Access access;
    private final JdbcTemplate jdbc;

    public CbmServiceImpl(DecisionRepository decisions, CbmResponseRepository responses, ProcFileRepository files,
                      FileStages stages, AuditService audit, Access access, JdbcTemplate jdbc) {
        this.decisions = decisions;
        this.responses = responses;
        this.files = files;
        this.stages = stages;
        this.audit = audit;
        this.access = access;
        this.jdbc = jdbc;
    }

    @Override
    public List<Map<String, Object>> pending() {
        access.requireCbm();
        return Rows.camel(jdbc.queryForList("""
                SELECT d.id AS decision_id, d.file_id, p.file_number, p.title, p.item_type, c.code AS committee_code,
                       s.session_number, d.recommendation_code, d.recommendation_text, d.recommended_bidder_ref,
                       d.contract_value, d.currency, d.votes_for, d.votes_against, d.votes_abstained,
                       d.required_votes, d.issued_at, p.stage
                  FROM decision d JOIN proc_file p ON p.id = d.file_id
                  JOIN committee_session s ON s.id = d.session_id JOIN committee c ON c.id = s.committee_id
                 WHERE d.status = 'ISSUED' AND NOT EXISTS (SELECT 1 FROM cbm_response r WHERE r.decision_id = d.id)
                 ORDER BY d.issued_at
                """));
    }

    @Override
    @Transactional
    public CbmResponse respond(UUID decisionId, ResponseRequest req) {
        access.requireCbm();
        Decision d = decisions.findById(decisionId).orElseThrow(() -> ApiException.notFound("Decision"));
        if (d.status != DecisionStatus.ISSUED) {
            throw ApiException.rule("decision.not_issued", "Only an issued recommendation can be answered");
        }
        if (!responses.findByDecisionIdOrderByRespondedAtAsc(d.id).isEmpty()) {
            throw ApiException.conflict("cbm.already_responded", "This recommendation has already been answered");
        }
        if (req.response() == null || !RESPONSES.contains(req.response())) {
            throw ApiException.badRequest("cbm.invalid_response", "Approve, return for correction, or other");
        }
        if (!"APPROVED".equals(req.response()) && (req.reasons() == null || req.reasons().isBlank())) {
            throw ApiException.badRequest("cbm.reasons_required", "Give written reasons");
        }
        CbmResponse r = new CbmResponse();
        r.decisionId = d.id;
        r.fileId = d.fileId;
        r.response = req.response();
        r.reasons = req.reasons() == null ? null : req.reasons().trim();
        r.respondedBy = access.userId();
        responses.saveAndFlush(r);
        audit.record("CBM_RESPONSE", "cbm_response", r.id, null, r);
        var file = files.findById(d.fileId).orElseThrow();
        switch (r.response) {
            case "APPROVED" -> stages.move(file, FileStages.CLOSED, "Approved by the CBM");
            case "RETURNED_FOR_CORRECTION" -> stages.move(file, FileStages.RETURNED_FOR_CORRECTION, r.reasons);
            default -> {
            }
        }
        return r;
    }
}
