package rw.gov.ptc.file.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import rw.gov.ptc.common.TenantEntity;
import rw.gov.ptc.file.enums.TableRequestStatus;

import java.time.Instant;
import java.util.UUID;

/** A member's request that a file be tabled; decided by the Chairperson. */
@Entity
@Table(name = "table_request")
public class TableRequest extends TenantEntity {

    @Column(columnDefinition = "uuid")
    public UUID committeeId;
    @Column(columnDefinition = "uuid")
    public UUID fileId;
    @Column(columnDefinition = "uuid")
    public UUID requestedBy;
    public Instant requestedAt = Instant.now();
    public String reason;
    @Enumerated(EnumType.STRING)
    public TableRequestStatus status = TableRequestStatus.PENDING;
    @Column(columnDefinition = "uuid")
    public UUID decidedBy;
    public Instant decidedAt;
    public String decisionNote;
    @Version
    public Long version;
}
