package rw.gov.ptc.evaluation.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import rw.gov.ptc.audit.service.AuditService;
import rw.gov.ptc.common.ApiException;
import rw.gov.ptc.config.component.ConfigKeys;
import rw.gov.ptc.config.service.ConfigService;
import rw.gov.ptc.evaluation.component.FileAccess;
import rw.gov.ptc.evaluation.entity.ChecklistResponse;
import rw.gov.ptc.evaluation.repository.ChecklistResponseRepository;
import rw.gov.ptc.evaluation.service.ChecklistService;
import rw.gov.ptc.file.entity.ProcFile;
import rw.gov.ptc.tenancy.TenantContext;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class ChecklistServiceImpl implements ChecklistService {

    private static final Set<String> ANSWERS = Set.of("YES", "NO", "NOT_APPLICABLE");

    private final FileAccess fileAccess;
    private final ChecklistResponseRepository responses;
    private final ConfigService config;
    private final AuditService audit;

    public ChecklistServiceImpl(FileAccess fileAccess, ChecklistResponseRepository responses, ConfigService config,
                            AuditService audit) {
        this.fileAccess = fileAccess;
        this.responses = responses;
        this.config = config;
        this.audit = audit;
    }

    private List<String> items(ProcFile f) {
        JsonNode all = config.snapshot().get(ConfigKeys.CHECKLIST_ITEMS);
        JsonNode forType = all.get(f.itemType);
        if (forType == null || !forType.isArray() || forType.isEmpty()) {
            throw ApiException.rule("checklist.not_applicable", "This item type has no checklist",
                    Map.of("itemType", f.itemType));
        }
        List<String> codes = new ArrayList<>();
        forType.forEach(n -> codes.add(n.asText()));
        return codes;
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, Object> get(UUID fileId) {
        return view(fileAccess.read(fileId));
    }

    private Map<String, Object> view(ProcFile f) {
        List<String> codes = items(f);
        Map<String, ChecklistResponse> byCode = new LinkedHashMap<>();
        responses.findByFileId(f.id).forEach(r -> byCode.put(r.itemCode, r));
        List<Map<String, Object>> items = new ArrayList<>();
        int yes = 0;
        int no = 0;
        int na = 0;
        for (String code : codes) {
            ChecklistResponse r = byCode.get(code);
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("code", code);
            m.put("labelKey", "checklist." + f.itemType + "." + code);
            m.put("answer", r == null ? null : r.answer);
            m.put("note", r == null ? null : r.note);
            m.put("answeredAt", r == null ? null : r.answeredAt);
            m.put("version", r == null ? null : r.version);
            items.add(m);
            if (r != null) {
                switch (r.answer) {
                    case "YES" -> yes++;
                    case "NO" -> no++;
                    default -> na++;
                }
            }
        }
        int answered = yes + no + na;
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("total", codes.size());
        summary.put("answered", answered);
        summary.put("yes", yes);
        summary.put("no", no);
        summary.put("notApplicable", na);
        summary.put("complete", answered == codes.size());
        // Any "no" is a finding the recommendation must address.
        summary.put("findings", no);
        summary.put("suggestedOutcome", answered < codes.size() ? null
                : no == 0 ? "AS_SUBMITTED" : "WITH_CHANGES_OR_RETURN");
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("fileId", f.id);
        out.put("itemType", f.itemType);
        out.put("items", items);
        out.put("summary", summary);
        return out;
    }

    @Override
    @Transactional
    public Map<String, Object> answer(UUID fileId, String itemCode, String answer, String note, Long version) {
        ProcFile f = fileAccess.write(fileId);
        if (!items(f).contains(itemCode)) {
            throw ApiException.badRequest("checklist.unknown_item", "Unknown checklist item", Map.of("item", itemCode));
        }
        if (!ANSWERS.contains(answer)) {
            throw ApiException.badRequest("checklist.invalid_answer", "Answer YES, NO or NOT_APPLICABLE");
        }
        if (!"YES".equals(answer) && (note == null || note.isBlank())) {
            throw ApiException.badRequest("checklist.note_required", "Explain the finding");
        }
        ChecklistResponse r = responses.findByFileIdAndItemCode(f.id, itemCode).orElseGet(ChecklistResponse::new);
        boolean created = r.isNew();
        if (!created) {
            FileAccess.checkVersion(version, r.version);
        }
        var before = created ? null : audit.snapshot(r);
        r.fileId = f.id;
        r.itemCode = itemCode;
        r.answer = answer;
        r.note = note == null || note.isBlank() ? null : note.trim();
        r.answeredBy = TenantContext.userOrNull();
        r.answeredAt = Instant.now();
        responses.save(r);
        audit.record(created ? "CREATE" : "UPDATE", "checklist_response", r.id, before, r);
        return view(f);
    }
}
