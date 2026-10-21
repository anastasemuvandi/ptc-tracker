package rw.gov.ptc.file.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import rw.gov.ptc.common.TenantEntity;

import java.time.Instant;
import java.util.UUID;

/** Move of a file between committees, with reason. */
@Entity
@Table(name = "file_reassignment")
public class FileReassignment extends TenantEntity {

    @Column(columnDefinition = "uuid")
    public UUID fileId;
    @Column(columnDefinition = "uuid")
    public UUID fromCommitteeId;
    @Column(columnDefinition = "uuid")
    public UUID toCommitteeId;
    public String reason;
    @Column(columnDefinition = "uuid")
    public UUID performedBy;
    public Instant performedAt = Instant.now();
}
