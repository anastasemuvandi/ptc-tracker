package rw.gov.ptc.evaluation;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import rw.gov.ptc.audit.AuditService;
import rw.gov.ptc.common.Time;
import rw.gov.ptc.file.FileStageHistory;
import rw.gov.ptc.file.FileStageHistoryRepository;
import rw.gov.ptc.file.ProcFile;
import rw.gov.ptc.file.ProcFileRepository;
import rw.gov.ptc.tenancy.TenantContext;

import java.time.Instant;

/**
 * Creates follow-on files raised from within a file (rejection of all bids
 * from an evaluation, cost claim from an extension) and records stage
 * transitions. File numbers come from the per-institution counter
 * FILE:&lt;year&gt; formatted F-YYYY/NNNN, never from UUIDs.
 */
@Component
public class FileFactory {

    private final JdbcTemplate jdbc;
    private final ProcFileRepository files;
    private final FileStageHistoryRepository history;
    private final AuditService audit;

    public FileFactory(JdbcTemplate jdbc, ProcFileRepository files, FileStageHistoryRepository history,
                       AuditService audit) {
        this.jdbc = jdbc;
        this.files = files;
        this.history = history;
        this.audit = audit;
    }

    public String nextFileNumber() {
        int year = Time.today().getYear();
        Integer n = jdbc.queryForObject("""
                INSERT INTO institution_counter (id, institution_id, counter_key, last_value)
                VALUES (?, app_current_institution(), ?, 1)
                ON CONFLICT (institution_id, counter_key)
                DO UPDATE SET last_value = institution_counter.last_value + 1
                RETURNING last_value
                """, Integer.class, rw.gov.ptc.common.Ids.newId(), "FILE:" + year);
        return "F-%d/%04d".formatted(year, n);
    }

    /** A new file derived from an existing one: same committee, officer and references. */
    public ProcFile derive(ProcFile source, String itemType, String title, String note) {
        ProcFile f = new ProcFile();
        f.committeeId = source.committeeId;
        f.fileNumber = nextFileNumber();
        f.itemType = itemType;
        f.tenderRef = source.tenderRef;
        f.contractId = source.contractId;
        f.title = title;
        f.procurementType = source.procurementType;
        f.procurementMethod = source.procurementMethod;
        f.procurementOfficerId = source.procurementOfficerId;
        f.dateReceived = Time.today();
        f.createdBy = TenantContext.userOrNull();
        files.save(f);
        recordStage(f, null, f.stage, note);
        audit.created("proc_file", f.id, f);
        return f;
    }

    public void changeStage(ProcFile f, String toStage, String note) {
        if (toStage.equals(f.stage)) {
            return;
        }
        var before = audit.snapshot(f);
        String from = f.stage;
        f.stage = toStage;
        files.save(f);
        recordStage(f, from, toStage, note);
        audit.record("STAGE_CHANGE", "proc_file", f.id, before, f);
    }

    private void recordStage(ProcFile f, String from, String to, String note) {
        FileStageHistory h = new FileStageHistory();
        h.fileId = f.id;
        h.fromStage = from;
        h.toStage = to;
        h.note = note;
        h.changedBy = TenantContext.userOrNull();
        h.changedAt = Instant.now();
        history.save(h);
    }
}
