package rw.gov.ptc.file;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import rw.gov.ptc.common.TenantEntity;

import java.time.Instant;
import java.util.UUID;

/** Append-only log of file stage transitions. */
@Entity
@Table(name = "file_stage_history")
public class FileStageHistory extends TenantEntity {

    @Column(columnDefinition = "uuid")
    public UUID fileId;
    public String fromStage;
    public String toStage;
    public String note;
    @Column(columnDefinition = "uuid")
    public UUID changedBy;
    public Instant changedAt = Instant.now();
}
