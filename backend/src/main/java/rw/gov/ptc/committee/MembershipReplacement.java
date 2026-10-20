package rw.gov.ptc.committee;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import rw.gov.ptc.common.TenantEntity;

import java.time.Instant;
import java.util.UUID;

/** Replacement of one or all members; reason is mandatory. */
@Entity
@Table(name = "membership_replacement")
public class MembershipReplacement extends TenantEntity {

    @Column(columnDefinition = "uuid")
    public UUID committeeId;
    public String scope;
    public String reason;
    @Column(columnDefinition = "uuid")
    public UUID performedBy;
    public Instant performedAt = Instant.now();
    @Column(columnDefinition = "uuid")
    public UUID regulatorTaskId;
}
