package rw.gov.ptc.file;

import org.springframework.stereotype.Component;
import rw.gov.ptc.audit.AuditService;
import rw.gov.ptc.common.ApiException;
import rw.gov.ptc.config.ConfigKeys;
import rw.gov.ptc.config.ConfigService;
import rw.gov.ptc.tenancy.TenantContext;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Stage workflow of files; the stage list per item type comes from configuration. */
@Component
public class FileStages {

    public static final String RECEIVED = "RECEIVED";
    public static final String SCHEDULED = "SCHEDULED";
    public static final String UNDER_REVIEW = "UNDER_REVIEW";
    public static final String RECOMMENDATION_ISSUED = "RECOMMENDATION_ISSUED";
    public static final String SUBMITTED_TO_CBM = "SUBMITTED_TO_CBM";
    public static final String CLOSED = "CLOSED";
    public static final String RETURNED_FOR_CORRECTION = "RETURNED_FOR_CORRECTION";

    private final ProcFileRepository files;
    private final FileStageHistoryRepository history;
    private final ConfigService config;
    private final AuditService audit;

    public FileStages(ProcFileRepository files, FileStageHistoryRepository history, ConfigService config,
                      AuditService audit) {
        this.files = files;
        this.history = history;
        this.config = config;
        this.audit = audit;
    }

    public List<String> stagesFor(String itemType) {
        var node = config.snapshot().get(ConfigKeys.WORKFLOW_STAGES);
        var list = node.has(itemType) ? node.get(itemType) : node.get("default");
        List<String> out = new ArrayList<>();
        list.forEach(n -> out.add(n.asText()));
        return out;
    }

    /** Moves the file to a stage (no-op if already there) and logs the transition. */
    public void move(ProcFile f, String toStage, String note) {
        if (toStage.equals(f.stage)) {
            return;
        }
        if (!stagesFor(f.itemType).contains(toStage)) {
            throw ApiException.badRequest("file.invalid_stage", "Unknown stage for this item type",
                    Map.of("stage", toStage));
        }
        var before = audit.snapshot(f);
        FileStageHistory h = new FileStageHistory();
        h.fileId = f.id;
        h.fromStage = f.stage;
        h.toStage = toStage;
        h.note = note;
        h.changedBy = TenantContext.userOrNull();
        history.save(h);
        f.stage = toStage;
        f.closedAt = CLOSED.equals(toStage) ? Instant.now() : null;
        files.saveAndFlush(f);
        audit.record("STAGE_CHANGE", "proc_file", f.id, before, f);
    }
}
