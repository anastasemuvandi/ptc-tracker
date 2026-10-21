package rw.gov.ptc.file.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import rw.gov.ptc.common.TenantEntity;

import java.time.Instant;
import java.util.UUID;

/** Procurement officer assigned to a file for a financial year. */
@Entity
@Table(name = "officer_assignment")
public class OfficerAssignment extends TenantEntity {

    @Column(columnDefinition = "uuid")
    public UUID fileId;
    @Column(columnDefinition = "uuid")
    public UUID officerId;
    public String financialYear;
    @Column(columnDefinition = "uuid")
    public UUID assignedBy;
    public Instant assignedAt = Instant.now();
}
