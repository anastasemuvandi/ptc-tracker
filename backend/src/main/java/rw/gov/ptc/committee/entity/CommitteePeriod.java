package rw.gov.ptc.committee.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import rw.gov.ptc.common.TenantEntity;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** One appointment of a committee, from its start to its (automatic) end. */
@Entity
@Table(name = "committee_period")
public class CommitteePeriod extends TenantEntity {

    @Column(columnDefinition = "uuid")
    public UUID committeeId;
    public LocalDate startsOn;
    public LocalDate endsOn;
    public String appointmentRef;
    public LocalDate closedOn;
    @Column(columnDefinition = "uuid")
    public UUID openedBy;
    public Instant openedAt = Instant.now();
}
