package rw.gov.ptc.file.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import rw.gov.ptc.common.ApiException;
import rw.gov.ptc.config.component.ConfigKeys;
import rw.gov.ptc.config.service.ConfigService;
import rw.gov.ptc.file.component.FileStages;
import rw.gov.ptc.file.dto.FileRequest;
import rw.gov.ptc.file.dto.Filter;
import rw.gov.ptc.file.dto.OfficerRequest;
import rw.gov.ptc.file.dto.ReassignRequest;
import rw.gov.ptc.file.dto.StageRequest;
import rw.gov.ptc.file.dto.TablingDecision;
import rw.gov.ptc.file.dto.TablingRequest;
import rw.gov.ptc.file.entity.ProcFile;
import rw.gov.ptc.file.entity.TableRequest;
import rw.gov.ptc.file.service.FileService;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api")
public class FileController {

    private final FileService files;
    private final FileStages stages;
    private final ConfigService config;

    public FileController(FileService files, FileStages stages, ConfigService config) {
        this.files = files;
        this.stages = stages;
        this.config = config;
    }

    @GetMapping("/files")
    public List<Map<String, Object>> list(@RequestParam(required = false) UUID committeeId,
                                          @RequestParam(required = false) String type,
                                          @RequestParam(required = false) UUID officerId,
                                          @RequestParam(required = false) String stage,
                                          @RequestParam(required = false) Boolean overdue,
                                          @RequestParam(required = false) String procurementType,
                                          @RequestParam(required = false) UUID contractId) {
        return files.list(new Filter(committeeId, type, officerId, stage, overdue, procurementType, contractId));
    }

    @PostMapping("/files")
    public ProcFile create(@RequestBody FileRequest req) {
        return files.create(req);
    }

    @GetMapping("/files/{id}")
    public Map<String, Object> get(@PathVariable UUID id) {
        return files.detail(id);
    }

    @PutMapping("/files/{id}")
    public ProcFile update(@PathVariable UUID id, @RequestBody FileRequest req) {
        return files.update(id, req);
    }

    @PostMapping("/files/{id}/stage")
    public ProcFile stage(@PathVariable UUID id, @RequestBody StageRequest req) {
        return files.changeStage(id, req.toStage(), req.note());
    }

    @PostMapping("/files/{id}/reassign")
    public ProcFile reassign(@PathVariable UUID id, @RequestBody ReassignRequest req) {
        return files.reassign(id, req.toCommitteeId(), req.reason());
    }

    @PostMapping("/files/{id}/officer")
    public ProcFile officer(@PathVariable UUID id, @RequestBody OfficerRequest req) {
        return files.assignOfficer(id, req.officerId(), req.financialYear());
    }

    @PostMapping("/files/{id}/table-requests")
    public TableRequest requestTabling(@PathVariable UUID id, @RequestBody TablingRequest req) {
        return files.requestTabling(id, req.reason());
    }

    @GetMapping("/committees/{id}/table-requests")
    public List<Map<String, Object>> tableRequests(@PathVariable UUID id, @RequestParam(required = false) String status) {
        return files.tableRequests(id, status);
    }

    @PostMapping("/table-requests/{id}/decide")
    public TableRequest decide(@PathVariable UUID id, @RequestBody TablingDecision req) {
        return files.decideTabling(id, req.accept(), req.note(), req.sessionId());
    }

    /** Workflow configuration of an item type: stages, recommendation codes, checklist. */
    @GetMapping("/workflow")
    public Map<String, Object> workflow(@RequestParam String type) {
        if (!type.matches("[A-I]")) {
            throw ApiException.badRequest("file.invalid_type", "Unknown item type");
        }
        var cfg = config.snapshot();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("type", type);
        out.put("stages", stages.stagesFor(type));
        out.put("recommendationCodes", strings(cfg.get(ConfigKeys.RECOMMENDATION_CODES).path(type)));
        out.put("checklist", strings(cfg.get(ConfigKeys.CHECKLIST_ITEMS).path(type)));
        out.put("targetWorkingDays", cfg.get(ConfigKeys.WORKFLOW_TARGET_WORKING_DAYS).path(type).asInt(0));
        return out;
    }

    private static List<String> strings(com.fasterxml.jackson.databind.JsonNode node) {
        List<String> out = new ArrayList<>();
        node.forEach(n -> out.add(n.asText()));
        return out;
    }
}
