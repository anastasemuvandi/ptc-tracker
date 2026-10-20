package rw.gov.ptc.evaluation;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import rw.gov.ptc.common.TenantEntity;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** Due diligence on the successful bidder (A5). */
@Entity
@Table(name = "due_diligence")
public class DueDiligence extends TenantEntity {

    @Column(columnDefinition = "uuid")
    public UUID fileId;
    public String bidderRef;
    public String performedByName;
    public LocalDate performedOn;
    public String itemsVerified;
    public String result;
    public boolean disqualified;
    public String disqualificationGround;
    @Column(columnDefinition = "uuid")
    public UUID recordedBy;
    public Instant recordedAt = Instant.now();
    @Version
    public Long version;
}
