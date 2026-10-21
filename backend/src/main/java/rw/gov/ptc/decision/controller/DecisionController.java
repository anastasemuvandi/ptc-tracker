package rw.gov.ptc.decision.controller;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import rw.gov.ptc.decision.dto.DeclarationRequest;
import rw.gov.ptc.decision.dto.DraftRequest;
import rw.gov.ptc.decision.dto.ResponseRequest;
import rw.gov.ptc.decision.dto.VoteRequest;
import rw.gov.ptc.decision.entity.CbmResponse;
import rw.gov.ptc.decision.entity.CoiDeclaration;
import rw.gov.ptc.decision.entity.Decision;
import rw.gov.ptc.decision.entity.Vote;
import rw.gov.ptc.decision.service.CbmService;
import rw.gov.ptc.decision.service.DecisionService;
import rw.gov.ptc.decision.service.DeclarationService;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api")
public class DecisionController {

    private final DecisionService decisions;
    private final DeclarationService declarations;
    private final CbmService cbm;

    public DecisionController(DecisionService decisions, DeclarationService declarations, CbmService cbm) {
        this.decisions = decisions;
        this.declarations = declarations;
        this.cbm = cbm;
    }

    @GetMapping("/agenda-items/{id}/declarations")
    public Map<String, Object> declarations(@PathVariable UUID id) {
        return declarations.list(id);
    }

    @PostMapping("/agenda-items/{id}/declarations")
    public CoiDeclaration declare(@PathVariable UUID id, @RequestBody DeclarationRequest req) {
        return declarations.declare(id, req);
    }

    @GetMapping("/declarations/export")
    public ResponseEntity<byte[]> export(@RequestParam UUID sessionId) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, "text/csv; charset=utf-8")
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"declarations.csv\"")
                .body(declarations.exportCsv(sessionId).getBytes(StandardCharsets.UTF_8));
    }

    @GetMapping("/agenda-items/{id}/decision")
    public Map<String, Object> decision(@PathVariable UUID id) {
        return decisions.forItem(id);
    }

    @PostMapping("/agenda-items/{id}/decision")
    public Decision draft(@PathVariable UUID id, @RequestBody DraftRequest req) {
        return decisions.draft(id, req);
    }

    @GetMapping("/decisions/{id}")
    public Decision get(@PathVariable UUID id) {
        return decisions.get(id);
    }

    @PutMapping("/decisions/{id}")
    public Decision update(@PathVariable UUID id, @RequestBody DraftRequest req) {
        return decisions.update(id, req);
    }

    @PostMapping("/decisions/{id}/open-voting")
    public Decision openVoting(@PathVariable UUID id) {
        return decisions.openVoting(id);
    }

    @PostMapping("/decisions/{id}/votes")
    public Vote vote(@PathVariable UUID id, @RequestBody VoteRequest req) {
        return decisions.vote(id, req);
    }

    @PostMapping("/decisions/{id}/close-voting")
    public Decision closeVoting(@PathVariable UUID id) {
        return decisions.closeVoting(id);
    }

    @PostMapping("/decisions/{id}/issue")
    public Decision issue(@PathVariable UUID id) {
        return decisions.issue(id);
    }

    @PostMapping("/decisions/{id}/submit-to-cbm")
    public Decision submit(@PathVariable UUID id) {
        return decisions.submitToCbm(id);
    }

    @GetMapping("/cbm/pending")
    public List<Map<String, Object>> pending() {
        return cbm.pending();
    }

    @PostMapping("/decisions/{id}/cbm-response")
    public CbmResponse respond(@PathVariable UUID id, @RequestBody ResponseRequest req) {
        return cbm.respond(id, req);
    }
}
