package rw.gov.ptc.evaluation.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import rw.gov.ptc.common.TenantEntity;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** Clarification request to a bidder; a bidder may explain, not improve. */
@Entity
@Table(name = "clarification")
public class Clarification extends TenantEntity {

    @Column(columnDefinition = "uuid")
    public UUID fileId;
    public String bidderRef;
    public LocalDate issuedOn;
    public String subject;
    public LocalDate responseReceivedOn;
    public String treatment;
    public String effect = "PENDING";
    @Column(columnDefinition = "uuid")
    public UUID recordedBy;
    public Instant recordedAt = Instant.now();
    @Version
    public Long version;
}
