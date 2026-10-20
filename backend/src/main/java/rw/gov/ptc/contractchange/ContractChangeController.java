package rw.gov.ptc.contractchange;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api")
public class ContractChangeController {

    private final ContractChangeService changes;
    private final ContractTimelineService timeline;

    public ContractChangeController(ContractChangeService changes, ContractTimelineService timeline) {
        this.changes = changes;
        this.timeline = timeline;
    }

    @GetMapping("/files/{id}/amendment")
    public Map<String, Object> amendment(@PathVariable UUID id) {
        return changes.getAmendment(id);
    }

    @PostMapping("/files/{id}/amendment")
    public Map<String, Object> createAmendment(@PathVariable UUID id,
                                               @RequestBody ContractChangeService.AmendmentInput req) {
        return changes.createAmendment(id, req);
    }

    @PutMapping("/files/{id}/amendment")
    public Map<String, Object> updateAmendment(@PathVariable UUID id,
                                               @RequestBody ContractChangeService.AmendmentInput req) {
        return changes.updateAmendment(id, req);
    }

    @PostMapping("/files/{id}/amendment/freeze")
    public Map<String, Object> freeze(@PathVariable UUID id) {
        return changes.freezeAmendment(id);
    }

    @GetMapping("/files/{id}/extension")
    public Map<String, Object> extension(@PathVariable UUID id) {
        return changes.getExtension(id);
    }

    @PostMapping("/files/{id}/extension")
    public Map<String, Object> createExtension(@PathVariable UUID id,
                                               @RequestBody ContractChangeService.ExtensionInput req) {
        return changes.createExtension(id, req);
    }

    @PutMapping("/files/{id}/extension")
    public Map<String, Object> updateExtension(@PathVariable UUID id,
                                               @RequestBody ContractChangeService.ExtensionInput req) {
        return changes.updateExtension(id, req);
    }

    @PostMapping("/files/{id}/extension/cost-claim")
    public Map<String, Object> costClaim(@PathVariable UUID id,
                                         @RequestBody ContractChangeService.CostClaimInput req) {
        return changes.raiseCostClaim(id, req);
    }

    @GetMapping("/contracts/{id}/timeline")
    public Map<String, Object> timeline(@PathVariable UUID id) {
        return timeline.timeline(id);
    }
}
