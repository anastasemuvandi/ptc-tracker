package rw.gov.ptc.committee.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import rw.gov.ptc.committee.enums.TaskStatus;
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
    @Enumerated(EnumType.STRING)
    public TaskStatus status = TaskStatus.OPEN;
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
