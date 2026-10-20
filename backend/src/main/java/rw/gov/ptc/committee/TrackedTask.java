package rw.gov.ptc.committee;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import rw.gov.ptc.common.TenantEntity;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** A tracked obligation, e.g. notifying the regulator after wholesale replacement. */
@Entity
@Table(name = "tracked_task")
public class TrackedTask extends TenantEntity {

    @Column(columnDefinition = "uuid")
    public UUID committeeId;
    public String kind;
    public String title;
    public String details;
    public LocalDate dueDate;
    public String status = "OPEN";
    public String entityType;
    @Column(columnDefinition = "uuid")
    public UUID entityId;
    @Column(columnDefinition = "uuid")
    public UUID assigneeId;
    @Column(columnDefinition = "uuid")
    public UUID createdBy;
    public Instant createdAt = Instant.now();
    public Instant doneAt;
    @Column(columnDefinition = "uuid")
    public UUID doneBy;
    public String doneReference;
    public String doneNote;
    @Version
    public Long version;
}
