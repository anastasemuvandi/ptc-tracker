package rw.gov.ptc.evaluation;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import rw.gov.ptc.common.TenantEntity;

import java.time.Instant;
import java.util.UUID;

/** Per-bidder pass/fail and rank at a stage, by bidder reference only. */
@Entity
@Table(name = "bidder_result")
public class BidderResult extends TenantEntity {

    @Column(columnDefinition = "uuid")
    public UUID stageId;
    @Column(columnDefinition = "uuid")
    public UUID fileId;
    public String bidderRef;
    public String result;
    public String failedRequirement;
    public String failureReason;
    public Integer rank;
    @Column(columnDefinition = "uuid")
    public UUID recordedBy;
    public Instant recordedAt = Instant.now();
    @Version
    public Long version;
}
