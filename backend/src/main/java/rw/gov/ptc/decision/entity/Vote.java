package rw.gov.ptc.decision.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import rw.gov.ptc.common.TenantEntity;

import java.time.Instant;
import java.util.UUID;

/** One member's vote; dissent reasons are mandatory for AGAINST/ABSTAIN and appear in the minutes. */
@Entity
@Table(name = "vote")
public class Vote extends TenantEntity {

    @Column(columnDefinition = "uuid")
    public UUID decisionId;
    @Column(columnDefinition = "uuid")
    public UUID membershipId;
    @Column(columnDefinition = "uuid")
    public UUID userId;
    public String choice;
    public String dissentReason;
    public Instant castAt = Instant.now();
    public Instant stepUpVerifiedAt;
    @Version
    public Long version;
}
